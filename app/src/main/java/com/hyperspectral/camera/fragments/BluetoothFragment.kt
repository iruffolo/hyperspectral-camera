package com.hyperspectral.camera.fragments

import android.R
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.hyperspectral.camera.CameraActivity
import com.hyperspectral.camera.databinding.BluetoothBinding
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

    // UUID - DO NOT CHANGE THIS
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

                // Add thread to main activity for access in other fragments
                CameraActivity.setBluetoothThread(ConnectedThread(socket))
                CameraActivity.getBluetoothThread()?.write("Connected to Android\n".toByteArray())
                Log.d("Bluetooth", "Thread connected")
                Toast.makeText(context, "Device Connected",
                    Toast.LENGTH_LONG).show()
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
                for (bt in paired) {
                    mBluetoothList.add(bt.name)
                    Log.d ("Bluetooth", "device: ${bt.name}")
                }
            }

            (mAdapter as ArrayAdapter<*>).notifyDataSetChanged()
        }


        // When an item on BT device list is clicked, start connection
        fragmentBtBinding.listView.setOnItemClickListener { parent, _, position, _ ->
            val name = parent.getItemAtPosition(position)
            val device = mBluetoothAdapter!!.bondedDevices.elementAt(position)

            Log.d("Bluetooth", "Connecting to BT device: $name")

            CameraActivity.getBluetoothThread()?.cancel()

            mConnectThread = ConnectThread(device)
            mConnectThread!!.run()
        }

        /** Go to camera screen */
        fragmentBtBinding.camButton.setOnClickListener {
            Log.d("Bluetooth", "BT to Camera")
            lifecycleScope.launchWhenStarted {
                Navigation.findNavController(requireActivity(), com.hyperspectral.camera.R.id.fragment_container).navigate(
                    BluetoothFragmentDirections.actionBluetoothFragmentToSelectorFragment())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}