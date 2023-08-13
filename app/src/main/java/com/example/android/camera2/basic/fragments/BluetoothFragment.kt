package com.example.android.camera2.basic.fragments

import android.Manifest
import android.R
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.example.android.camera2.basic.CameraActivity
import com.example.android.camera2.basic.databinding.BluetoothBinding
import java.io.IOException
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList


class BluetoothFragment : Fragment() {

    private var mBluetoothAdapter: BluetoothAdapter? = null

    private var _fragmentBtBinding: BluetoothBinding? = null
    private val fragmentBtBinding get() = _fragmentBtBinding!!

    private var mBluetoothDevices : ArrayList<BluetoothDevice>? = ArrayList()

    private val mBluetoothList = ArrayList<Any>()

    private var mAdapter : ArrayAdapter<*>? = null

    // Create a BroadcastReceiver for ACTION_FOUND.
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice

                    mBluetoothDevices?.add(device)

                    if (device.name != null)
                        mBluetoothList.add(device)

                    mAdapter?.notifyDataSetChanged()
                }
            }
        }
    }

    private val mUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Thread to make BT connection
    private var mConnectThread : ConnectThread? = null

    inner class ConnectThread(device: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createInsecureRfcommSocketToServiceRecord(mUUID)
        }

        override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            mBluetoothAdapter?.cancelDiscovery()

            mmSocket?.let { socket ->
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                socket.connect()
                Log.d("Bluetooth", "Thread connected")

                // Add thread to main activity for access in other fragments
                CameraActivity.setBluetoothThread(ConnectedThread(socket))
                CameraActivity.getBluetoothThread()?.write("Connected to Android\n".toByteArray())
            }
        }
    }

    inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mTag: String = "BT Thread"

        // Call this from the main activity to send data to the remote device.
        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {
                Log.e(mTag, "Error occurred when sending data", e)
                // Raise toast msg with failure
                Toast.makeText(context, "Couldn't send data to other BT device",
                    Toast.LENGTH_LONG).show()
                return
            }
        }

        // Call this method from the main activity to shut down the connection.
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(mTag, "Could not close the connect socket", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init the bluetooth
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            val activity = activity
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show()
            activity!!.finish()
        }

        if (!mBluetoothAdapter?.isEnabled!!) {
            val turnOn = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(turnOn, 0)
            Toast.makeText(context, "BT Turned on",Toast.LENGTH_LONG).show()
        }
        val getVis = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        startActivityForResult(getVis, 0)

        // Register for broadcasts when a device is discovered.
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        activity?.registerReceiver(receiver, filter)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentBtBinding = BluetoothBinding.inflate(inflater, container, false)
        return fragmentBtBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mAdapter =
            ArrayAdapter<Any?>(requireContext(), R.layout.simple_list_item_1,
                mBluetoothList as List<Any?>
            )
        fragmentBtBinding.listView.adapter = mAdapter

        // Lists all previously paired BT devices
        fragmentBtBinding.listButton.setOnClickListener {
            Log.d("Bluetooth", "Listing BT devices")

            val paired: Set<BluetoothDevice>? = mBluetoothAdapter?.bondedDevices

            mBluetoothList.clear()
            if (paired != null) {
                for (bt in paired) mBluetoothList.add(bt)
            }

            (mAdapter as ArrayAdapter<*>).notifyDataSetChanged()
        }

        // Does a search for discoverable devices, lists by mac address
        fragmentBtBinding.findButton.setOnClickListener {
            Log.d("Bluetooth", "Find BT Devices")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ActivityCompat.requestPermissions(
                        requireContext() as Activity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        1)
            }

            mBluetoothAdapter?.startDiscovery()
            mBluetoothList.clear()

            (mAdapter as ArrayAdapter<*>).notifyDataSetChanged()
        }

        fragmentBtBinding.camButton.setOnClickListener {
            Log.d("Bluetooth", "BT to Camera")
            lifecycleScope.launchWhenStarted {
                Navigation.findNavController(requireActivity(), com.example.android.camera2.basic.R.id.fragment_container).navigate(
                    BluetoothFragmentDirections.actionBluetoothFragmentToSelectorFragment())
            }
        }

        fragmentBtBinding.calibButton.setOnClickListener {
            Log.d("Bluetooth", "BT to Calibration")
        }

        // When an item on BT device list is clicked, start connection
        fragmentBtBinding.listView.setOnItemClickListener { parent, _, position, _ ->
            val device = parent.getItemAtPosition(position) as BluetoothDevice
            val name = device.name
            Log.d("Bluetooth", "Connecting to BT device: $name")

            CameraActivity.getBluetoothThread()?.cancel()

            mConnectThread = ConnectThread(device)
            mConnectThread!!.run()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Unregister the ACTION_FOUND receiver.
        activity?.unregisterReceiver(receiver)
    }
}