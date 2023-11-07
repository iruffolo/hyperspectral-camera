/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hyperspectral.camera.fragments

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.*
import android.widget.SeekBar
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.example.android.camera.utils.OrientationLiveData
import com.example.android.camera.utils.computeExifOrientation
import com.hyperspectral.camera.CameraActivity
import com.hyperspectral.camera.R
import com.hyperspectral.camera.databinding.FragmentCameraBinding
import com.hyperspectral.camera.utils.AutoExposure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class CameraFragment : Fragment() {

    /** Android ViewBinding */
    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    /** AndroidX navigation arguments */
    private val args: CameraFragmentArgs by navArgs()

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(args.cameraId)
    }

    /** Readers used as buffers for camera still shots */
    private lateinit var imageReader: ImageReader

    /** [HandlerThread] where all camera operations run */
    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)

    /** Performs recording animation of flashing screen */
    private val animationTask: Runnable by lazy {
        Runnable {
            // Flash white animation
            fragmentCameraBinding.overlay.background = Color.argb(150, 255, 255, 255).toDrawable()
            // Wait for ANIMATION_FAST_MILLIS
            fragmentCameraBinding.overlay.postDelayed({
                // Remove white flash animation
                fragmentCameraBinding.overlay.background = null
            }, CameraActivity.ANIMATION_FAST_MILLIS)
        }
    }

    /** [HandlerThread] where all buffer reading operations run */
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    /** [Handler] corresponding to [imageReaderThread] */
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    /** The [CameraDevice] that will be opened in this fragment */
    private lateinit var camera: CameraDevice

    /** Internal reference to the ongoing [CameraCaptureSession] configured with our parameters */
    private lateinit var session: CameraCaptureSession

    /** Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData

    private lateinit var mPreviewRequest: CaptureRequest.Builder

    private var mConfigMenu : Boolean = false

    private var mBT : BluetoothFragment.ConnectedThread? = null

    /** Current Mode */
    private enum class CameraMode {GT, RS, W}
    private var mMode : CameraMode = CameraMode.RS
    private var mCommandDelay: Long = 30
    private var mNumGtPhotos : Int = 12
    private var mNumRsPhotos : Int = 1

    private var mSceneName : String = "ColorChecker"

    /** Camera Capture Parameters **/
    private val mRollingShutterTime : Float = 10.7F
    private var mSensorExposureTime : Long = 60000
    private var mSensitivity : Int = 2000
    // private var mShutterSpeed : Int = 0
    private var mControlMode : Int = CaptureRequest.CONTROL_MODE_AUTO
    private var mAutoExposureMode : Int = CaptureRequest.CONTROL_AE_MODE_OFF
    private var mAutoFocusMode: Int = CaptureRequest.CONTROL_AF_MODE_AUTO
    private var mAutoFocusTrigger: Int = CaptureRequest.CONTROL_AF_TRIGGER_START

    /** LED Parameters (times in microseconds) **/
    private var mLedDebugDelay : Int = 1000 // This is MS
    private var mLedOnTime : Int = 10
    private var mLedOffTime : Int = 0
    private var mWhiteOnMultiple: Int = 1
    private var mNumLedMultiplex: Int = 1
    private var mNumBlackBands: Int = 2
    private var mNumRows: Int = 500

    private lateinit var mAE : AutoExposure

    private var mAEToggle : Boolean = false
    private var mAFToggle : Boolean = true
    private var mAFState : Int = CaptureRequest.CONTROL_AF_STATE_INACTIVE

    private lateinit var mSize : Size

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentCameraBinding.viewFinder!!.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

            @RequiresApi(Build.VERSION_CODES.P)
            override fun surfaceCreated(holder: SurfaceHolder) {
                // Selects appropriate preview size and configures view finder
//                val previewSize = getPreviewOutputSize(
//                    fragmentCameraBinding.viewFinder.display,
//                    characteristics,
//                    SurfaceHolder::class.java
//                )
                Log.d(TAG, "View finder size: ${fragmentCameraBinding.viewFinder.width} x ${fragmentCameraBinding.viewFinder.height}")

                // To ensure that size is set, initialize camera in the view's thread
                view.post { initializeCamera() }
            }
        })

        // Used to rotate the output media to match device orientation
        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
            observe(viewLifecycleOwner) { orientation ->
                Log.d(TAG, "Orientation changed: $orientation")
            }
        }

        mBT = CameraActivity.getBluetoothThread()

        initializeButtons()
    }

    // Tac = (px - 1) * Trs + Te
    // Num rows = Tac / (N * (Ton + T off))
    private fun calcNumRows() {
        var tAcq = (mSize.height - 1) * mRollingShutterTime + (mSensorExposureTime/1000)
        mNumRows = (tAcq / (mNumBlackBands * (mLedOnTime + mLedOffTime))).toInt()
    }

    private fun initializeButtons() {

        fragmentCameraBinding.aeText?.text = getString(R.string.ae_text, "0")
        fragmentCameraBinding.aeRefreshButton?.setOnClickListener {
            Log.d("Auto Exposure", "Refreshing")

            imageReader.setOnImageAvailableListener(mAE.autoExposureListener, imageReaderHandler)

            val captureRequest = session.device.createCaptureRequest(
                CameraDevice.TEMPLATE_MANUAL).apply { addTarget(imageReader.surface) }

            // Set parameters for ISO, exposure time, etc
            setCaptureParams(captureRequest)

            session.capture(captureRequest.build(), null, cameraHandler)
        }

        /** Button to open configuration menu */
        fragmentCameraBinding.configButton?.setOnClickListener {
            Log.d("Config", "Changing to config screen")
            if (mConfigMenu) {
                fragmentCameraBinding.SettingsLayout?.visibility = View.GONE
                fragmentCameraBinding.captureButton.visibility = View.VISIBLE
            } else {
                fragmentCameraBinding.SettingsLayout?.visibility = View.VISIBLE
                fragmentCameraBinding.captureButton.visibility = View.GONE
            }
            mConfigMenu = !mConfigMenu
        }

        /** ISO/GAIN Slider */
        val gainRange: Range<Int> = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)!!
        Log.d("Ian", "ISO time low: " + gainRange.lower + "\thigh: " + gainRange.upper)
        fragmentCameraBinding.sensitivityIso?.min = gainRange.lower
        fragmentCameraBinding.sensitivityIso?.max = gainRange.upper
        fragmentCameraBinding.sensitivityIso?.progress = mSensitivity
        fragmentCameraBinding.sensitivityISOText?.text = getString(R.string.iso_text, mSensitivity)
        fragmentCameraBinding.sensitivityIso?.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    mSensitivity = progress

                    session.stopRepeating()
                    setCaptureParams(mPreviewRequest) // Update capture params with sensitivity
                    session.setRepeatingRequest(mPreviewRequest.build(), captureCallback, cameraHandler)

                    // updated continuously as the user slides the thumb
                    fragmentCameraBinding.sensitivityISOText?.text = getString(R.string.iso_text, progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

        /** Exposure time slider */
        val etRange: Range<Long> = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)!!
        Log.d("Ian", "exposure time low: " + etRange.lower + "\thigh: " + etRange.upper)
        fragmentCameraBinding.exposureTime?.min = etRange.lower.toInt()
        fragmentCameraBinding.exposureTime?.max = etRange.upper.toInt() / 3000
        fragmentCameraBinding.exposureTime?.progress = mSensorExposureTime.toInt()
        fragmentCameraBinding.exposureTimeText?.text = getString(R.string.exposure_text,
                                                                mSensorExposureTime,
                                                                1000000000/mSensorExposureTime)
        fragmentCameraBinding.exposureTime?.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    mSensorExposureTime = progress.toLong()
                    // updated continuously as the user slides the thumb
                    fragmentCameraBinding.exposureTimeText?.text = getString(R.string.exposure_text,
                                                                            mSensorExposureTime,
                                                                            1000000000/mSensorExposureTime)

                    session.stopRepeating()
                    setCaptureParams(mPreviewRequest) // Update capture params with exposure time
                    session.setRepeatingRequest(mPreviewRequest.build(), captureCallback, cameraHandler)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

        /** Listen to the image capture button */
        fragmentCameraBinding.captureButton.setOnClickListener {

            // Disable click listener to prevent multiple requests simultaneously in flight
            it.isEnabled = false

            // Regular Mode
            when(mMode) {
                // White LED mode
                CameraMode.W -> {
                    takePhotoMode(1, "W")
                }
                // Ground truth mode
                CameraMode.GT -> {
                    takePhotoMode(mNumGtPhotos, "GT")
                }
                // Rolling shutter mode
                CameraMode.RS -> {
                    takePhotoMode(mNumRsPhotos, "RS")
                }
            }

            // Re-enable click listener after photo is taken
            it.post { it.isEnabled = true }
        }

        /** Slider for number of rolling shutter images  */
        fragmentCameraBinding.rsNumImages?.progress = mNumRsPhotos - 1
        fragmentCameraBinding.rsNumImagesText?.text = getString(R.string.rs_num_images_text, mNumRsPhotos)
        fragmentCameraBinding.rsNumImages?.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    mNumRsPhotos = progress + 1
                    fragmentCameraBinding.rsNumImagesText?.text = getString(R.string.rs_num_images_text, mNumRsPhotos)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

        /** Ground truth mode toggle switch */
        fragmentCameraBinding.gtSwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // First disable W mode if it is on
                fragmentCameraBinding.wSwitch?.isChecked = false
                // Turn on AE
                mAEToggle = true
                // The toggle is enabled
                mMode = CameraMode.GT
                Log.d("Mode Switch","Camera mode set to Ground Truth (GT)")
            } else {
                // Turn off AE
                mAEToggle = false
                // The toggle is disabled
                mMode = CameraMode.RS
                Log.d("Mode Switch","Camera mode set to Rolling Shutter (RS)")
            }
            fragmentCameraBinding.aeSwitch?.isChecked = mAEToggle
        }

        /** Ground truth mode toggle switch */
        fragmentCameraBinding.wSwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // First disable GT mode if it is on
                fragmentCameraBinding.gtSwitch?.isChecked = false
                // Turn on AE
                mAEToggle = true
                // The toggle is enabled
                mMode = CameraMode.W
                Log.d("Mode Switch","Camera mode set to White LED (W)")
            } else {
                // Turn off AE
                mAEToggle = false
                // The toggle is disabled
                mMode = CameraMode.RS
                Log.d("Mode Switch","Camera mode set to Rolling Shutter (RS)")
            }
            fragmentCameraBinding.aeSwitch?.isChecked = mAEToggle
        }

        /** DEBUG MODE */
        /** Debug mode toggle switch */
        fragmentCameraBinding.debugSwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                Log.d("Debug Switch", "ON")
                mBT?.write("DEBUG:${mLedDebugDelay}\n".toByteArray())
            }
        }
        /** Debug mode delay text input */
        fragmentCameraBinding.debugLedDelay?.hint = getString(R.string.debug_hint, mLedDebugDelay)
        fragmentCameraBinding.debugLedDelay?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                val text = fragmentCameraBinding.debugLedDelay!!.text.toString()

                try {
                    mLedDebugDelay = text.toInt()
                    Log.d("IanLED", "New debug delay = $mLedDebugDelay")
                } catch (nfe: NumberFormatException) {
                    Log.e(TAG, "Could not parse text field", nfe)
                }
            }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })

        /** RESET LEDs */
        fragmentCameraBinding.reset?.setOnClickListener {
            Log.d ("IanLED", "Reset LEDs")
            mBT?.write("RESET:0\n".toByteArray())
        }

        /** LED Parameter Settings */
        /** Number of LEDs to multiplex on at the same time*/
        fragmentCameraBinding.rsNumLedMultiplex?.progress = mNumLedMultiplex - 1
        fragmentCameraBinding.rsNumLedMultiplexText?.text = getString(R.string.rs_num_led_mplx_text,
            mNumLedMultiplex)
        fragmentCameraBinding.rsNumLedMultiplex?.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    mNumLedMultiplex = progress + 1
                    fragmentCameraBinding.rsNumLedMultiplexText?.text = getString(R.string.rs_num_led_mplx_text,
                        mNumLedMultiplex)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        /** Input for LED on time */
        fragmentCameraBinding.ledOnTime?.hint = getString(R.string.led_on_hint, mLedOnTime)
        fragmentCameraBinding.ledOnTime?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                val text = fragmentCameraBinding.ledOnTime!!.text.toString()

                try {
                    mLedOnTime = text.toInt()
                    Log.d("IanLED", "New LED on time = $mLedOnTime")
                    calcNumRows()
                } catch (nfe: NumberFormatException) {
                    Log.e(TAG, "Could not parse text field", nfe)
                }
            }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
        /** Input for LED off time */
        fragmentCameraBinding.ledOffTime?.hint = getString(R.string.led_off_hint, mLedOffTime)
        fragmentCameraBinding.ledOffTime?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                val text = fragmentCameraBinding.ledOffTime!!.text.toString()

                try {
                    mLedOffTime = text.toInt()
                    calcNumRows()
                    Log.d("IanLED", "New LED off time = $mLedOffTime")
                } catch (nfe: NumberFormatException) {
                    Log.e(TAG, "Could not parse text field", nfe)
                }
            }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
        /** Input for White LED time multiple i.e. white light is on for X times longer than others */
        fragmentCameraBinding.whiteOnMultiple?.hint = getString(R.string.white_on_hint, mWhiteOnMultiple)
        fragmentCameraBinding.whiteOnMultiple?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                val text = fragmentCameraBinding.whiteOnMultiple!!.text.toString()

                try {
                    mWhiteOnMultiple = text.toInt()
                    Log.d("IanLED", "New while LED multiple = $mWhiteOnMultiple")
                } catch (nfe: NumberFormatException) {
                    Log.e(TAG, "Could not parse text field", nfe)
                }
            }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
        /** Button to send LED timing changes on bluetooth */
        fragmentCameraBinding.ledSendUpdate?.setOnClickListener {
            Log.d("IanLED", "Sending new LED params: ON/OFF=${mLedOnTime}/${mLedOffTime}"
                + "\tWhite On=${mWhiteOnMultiple} \tLed to multiplex=${mNumLedMultiplex}")

            mBT?.write("LEDON:${mLedOnTime}\n".toByteArray())
            mBT?.write("LEDOFF:${mLedOffTime}\n".toByteArray())
            mBT?.write("WHITEON:${mWhiteOnMultiple}\n".toByteArray())
            mBT?.write("LEDMPLX:${mNumLedMultiplex}\n".toByteArray())
            mBT?.write("NROWS:${mNumRows}\n".toByteArray())
        }

        /** Scene name text input */
        fragmentCameraBinding.sceneName?.hint = getString(R.string.scene_name, mSceneName)
        fragmentCameraBinding.sceneName?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                var text = fragmentCameraBinding.sceneName!!.text.toString()
                // remove whitespace
                text = text.replace("\\s".toRegex(), "")

                mSceneName = text
                Log.d(tag, "New scene name : $mSceneName")
                fragmentCameraBinding.sceneName?.hint = getString(R.string.scene_name, mSceneName)
            }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })

        /** Toggle for autofocus */
        fragmentCameraBinding.afSwitch?.isChecked = mAFToggle
        fragmentCameraBinding.afSwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // The toggle is enabled
                mAFToggle = true
                Log.d("AF", "AF clamp enabled")
            } else {
                // The toggle is disabled
                mAFToggle = false
                Log.d("AF", "AF clamp disabled")
            }
        }

        /** Toggle for autofocus */
        fragmentCameraBinding.aeSwitch?.isChecked = mAEToggle
        fragmentCameraBinding.aeSwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (mMode != CameraMode.RS) {
                if (isChecked) {
                    // The toggle is enabled
                    mAEToggle = true
                    Log.d("Ian AE", "AE enabled")
                } else {
                    // The toggle is disabled
                    mAEToggle = false
                    Log.d("Ian AE", "AE disabled")
                }
            }
            else {
                fragmentCameraBinding.aeSwitch?.isChecked = false
            }
            // Reset camera preview
            session.stopRepeating()
            setCaptureParams(mPreviewRequest)
            session.setRepeatingRequest(mPreviewRequest.build(), captureCallback, cameraHandler)
        }
    }

    /** Preview callback to determine focus has been locked **/
    private val captureCallback: CameraCaptureSession.CaptureCallback =
        object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult
            ) {
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                mAFState = result.get(CaptureResult.CONTROL_AF_STATE)!!

                var et = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)!!
                if (mSensorExposureTime != et)
                {
                    mSensorExposureTime = et
                    calcNumRows()
//                    Log.d("Num rowwsss", "$mNumRows")
                }
                // Log.d("Preview exposure", "${result.get(CaptureResult.SENSOR_EXPOSURE_TIME)}")
            }
        }


    /**
     * Begin all camera operations in a coroutine in the main thread. This function:
     * - Opens the camera
     * - Configures the camera session
     * - Starts the preview by dispatching a repeating capture request
     * - Sets up the still image capture listeners
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        // Open the selected camera

        Log.d("Ian", "Initializing Camera ID ${args.cameraId}")
        mBT?.write("Initializing Camera\n".toByteArray())

        camera = openCamera(cameraManager, args.cameraId, cameraHandler)

        // Initialize an image reader which will be used to capture still photos
        mSize = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                .getOutputSizes(args.pixelFormat).maxByOrNull { it.height * it.width }!!

//        mAE = AutoExposure {x: String ->
//            fragmentCameraBinding.aeText?.text = getString(R.string.ae_text, x) }
        mAE = AutoExposure {x: String -> Log.d("Callback", "$x")
                fragmentCameraBinding.aeText!!.post(Runnable {
                    fragmentCameraBinding.aeText?.text = getString(R.string.ae_text, x)
                })
            }

        Log.d("Pixels", "${args.pixelFormat}")

        imageReader = ImageReader.newInstance(
                mSize.width, mSize.height, args.pixelFormat, IMAGE_BUFFER_SIZE)

        Log.d("Camera Size", "Width: ${mSize.width}, Height: ${mSize.height}")

        // Creates list of Surfaces where the camera will output frames
        val targets = listOf(fragmentCameraBinding.viewFinder.holder.surface,
            imageReader.surface)

        // Start a capture session using our open camera and list of Surfaces where frames will go
        session = createCaptureSession(camera, targets, cameraHandler)

        mPreviewRequest = camera.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(fragmentCameraBinding.viewFinder.holder.surface)
        }

        // Set all the appropriate camera capture settings for image preview
        setCaptureParams(mPreviewRequest)

        // This will keep sending the capture request as frequently as possible until the
        // session is torn down or session.stopRepeating() is called
        session.setRepeatingRequest(mPreviewRequest.build(), captureCallback, cameraHandler)
    }

    private fun setCaptureParams(request: CaptureRequest.Builder) {
        if (mAEToggle) {
            // Force AF/AE on
            request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            request.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        } else {
            request.set(CaptureRequest.CONTROL_MODE, mControlMode)
            request.set(CaptureRequest.CONTROL_AE_MODE, mAutoExposureMode)
            request.set(CaptureRequest.CONTROL_AF_MODE, mAutoFocusMode)
            request.set(CaptureRequest.CONTROL_AF_TRIGGER, mAutoFocusTrigger)
            request.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)

            request.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mSensorExposureTime)
            request.set(CaptureRequest.SENSOR_SENSITIVITY, mSensitivity)
        }
    }

    private fun takePhotoMode(numPhotos: Int, mode: String)
    {
        // Perform I/O heavy operations in a different scope
        lifecycleScope.launch(Dispatchers.IO) {
            for (i in 0 until numPhotos) {
                mBT?.write("${mode}:$i\n".toByteArray())
                delay(mCommandDelay*20) // Delay to give time for LEDs to turn on

                // Wait for auto focus to lock
                if (mAFToggle) {
                    while (mAFState != CaptureRequest.CONTROL_AF_STATE_FOCUSED_LOCKED) {
                    }
                }

                takePhoto(mode).use { result ->
                    // Save the result to disk
                    saveResult(result, "${mode}_$i")
                }
                delay(mCommandDelay) // Delay to give time for LEDs to turn off
                mBT?.write("RESET:0\n".toByteArray())
            }
        }
    }

    /**
     * Helper function used to capture a still image using the [CameraDevice.TEMPLATE_STILL_CAPTURE]
     * template. It performs synchronization between the [CaptureResult] and the [Image] resulting
     * from the single capture, and outputs a [CombinedCaptureResult] object.
     */
    private suspend fun takePhoto(mode: String):
            CombinedCaptureResult = suspendCoroutine { cont ->

        // Flush any images left in the image reader
        @Suppress("ControlFlowWithEmptyBody")
        while (imageReader.acquireNextImage() != null) {
        }

        // Start a new image queue
        val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            Log.d(TAG, "Image available in queue: ${image.timestamp}")
            imageQueue.add(image)
        }, imageReaderHandler)

        val captureRequest = session.device.createCaptureRequest(
                CameraDevice.TEMPLATE_MANUAL).apply { addTarget(imageReader.surface) }

        // Set parameters for ISO, exposure time, etc
        setCaptureParams(captureRequest)

        session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {

            override fun onCaptureStarted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    timestamp: Long,
                    frameNumber: Long) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
                fragmentCameraBinding.viewFinder.post(animationTask)
            }

            override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult) {
                super.onCaptureCompleted(session, request, result)
                val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                Log.d(TAG, "Capture result received: $resultTimestamp")

                Log.d("EXPOSURE", "${result.get(CaptureResult.SENSOR_EXPOSURE_TIME)}");

                // Set a timeout in case image captured is dropped from the pipeline
                val exc = TimeoutException("Image dequeuing took too long")
                val timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                imageReaderHandler.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                // Loop in the coroutine's context until an image with matching timestamp comes
                // We need to launch the coroutine context again because the callback is done in
                //  the handler provided to the `capture` method, not in our coroutine context
                @Suppress("BlockingMethodInNonBlockingContext")
                lifecycleScope.launch(cont.context) {
                    while (true) {

                        // Dequeue images while timestamps don't match
                        val image = imageQueue.take()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                image.format != ImageFormat.DEPTH_JPEG &&
                                image.timestamp != resultTimestamp) continue
                        Log.d(TAG, "Matching image dequeued: ${image.timestamp}")

                        // Unset the image reader listener
                        imageReaderHandler.removeCallbacks(timeoutRunnable)
                        imageReader.setOnImageAvailableListener(null, null)

                        // Clear the queue of images, if there are left
                        while (imageQueue.size > 0) {
                            imageQueue.take().close()
                        }

                        // Compute EXIF orientation metadata
                        val rotation = relativeOrientation.value ?: 0
                        val mirrored = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                                CameraCharacteristics.LENS_FACING_FRONT
                        val exifOrientation = computeExifOrientation(rotation, mirrored)

                        // Build the result and resume progress
                        cont.resume(CombinedCaptureResult(
                                image, result, exifOrientation, imageReader.imageFormat))

                        // There is no need to break out of the loop, this coroutine will suspend
                    }
                }
            }
        }, cameraHandler)
    }

    /** Helper function used to save a [CombinedCaptureResult] into a [File] */
    private suspend fun saveResult(result: CombinedCaptureResult, label: String): String = suspendCoroutine { cont ->
        when (result.format) {

            // When the format is JPEG or DEPTH JPEG we can simply save the bytes as-is
            ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                val buffer = result.image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                try {
                    val ts = Instant.now().epochSecond
                    var filename = "${mSceneName}_${ts}_${label}"
                    filename += "_${mLedOnTime}_${mLedOffTime}_${mNumLedMultiplex}"
                    filename += "_${mSensorExposureTime}_${mSensitivity}.jpg"

                    val resolver = requireContext().contentResolver
                    val contentValues = ContentValues().apply{
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/HyperspectralCam")
                    }
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                    resolver.openOutputStream(uri!!).use {it!!.write(bytes)}
                    cont.resume("Test")
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write JPEG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }

            // When the format is RAW we use the DngCreator utility library
            ImageFormat.RAW_SENSOR -> {
                val dngCreator = DngCreator(characteristics, result.metadata)
                try {
                    val ts = Instant.now().epochSecond
                    var filename = "${mSceneName}_${ts}_${label}"
                    filename += "_${mLedOnTime}_${mLedOffTime}_${mWhiteOnMultiple}_${mNumLedMultiplex}"
                    filename += "_${mSensorExposureTime}_${mSensitivity}_${mNumRows}.dng"

                    val resolver = requireContext().contentResolver
                    val contentValues = ContentValues().apply{
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/HyperspectralCam")
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/dng")
                    }
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    resolver.openOutputStream(uri!!).use {
                        dngCreator.writeImage(it!!, result.image)
                    }
                    Log.d("Ian", "Image saved: $uri")
                    cont.resume("Done")
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write DNG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }

            // No other formats are supported by this sample
            else -> {
                val exc = RuntimeException("Unknown image format: ${result.image.format}")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }
    }

    /** Opens the camera and returns the opened device (as the result of the suspend coroutine) */
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
                requireActivity().finish()
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    /**
     * Starts a [CameraCaptureSession] and returns the configured session (as the result of the
     * suspend coroutine
     */
    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->

        // Create a capture session using the predefined targets; this also involves defining the
        // session state callback to be notified of when the session is ready
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    override fun onStop() {
        super.onStop()
        try {
            camera.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
        imageReaderThread.quitSafely()
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
    }

    companion object {
        private val TAG = CameraFragment::class.java.simpleName

        /** Maximum number of images that will be held in the reader's buffer */
        private const val IMAGE_BUFFER_SIZE: Int = 3

        /** Maximum time allowed to wait for the result of an image capture */
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000

        /** Helper data class used to hold capture metadata with their associated image */
        data class CombinedCaptureResult(
                val image: Image,
                val metadata: CaptureResult,
                val orientation: Int,
                val format: Int
        ) : Closeable {
            override fun close() = image.close()
        }

    }
}
