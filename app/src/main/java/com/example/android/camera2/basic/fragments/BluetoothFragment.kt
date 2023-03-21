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
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListAdapter
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.example.android.camera2.basic.databinding.BluetoothBinding
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList


class BluetoothFragment : Fragment() {

    private var mBluetoothAdapter: BluetoothAdapter? = null

    private var mBluetoothDevice: BluetoothDevice? = null

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
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                    mBluetoothDevices?.add(device)

                    if (device.name != null)
                        mBluetoothList?.add(device)

                    mAdapter?.notifyDataSetChanged()
                }
            }
        }
    }

//    private val mUUID : UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    private val uuid: ParcelUuid = ParcelUuid.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    private inner class ConnectThread(device: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createInsecureRfcommSocketToServiceRecord(uuid.uuid)
        }

        public override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            mBluetoothAdapter?.cancelDiscovery()

            mmSocket?.let { socket ->
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                socket.connect()

                Log.d("Bluetooth", "Thread connected")

                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
//                manageMyConnectedSocket(socket)
            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e("Bluetooth Socket", "Could not close the client socket", e)
            }
        }
    }

    private var mConnectThread : ConnectThread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("BLUETOOTH FRAG", "ok we got here")

        // Init the bluetooth
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            val activity = activity
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show()
            activity!!.finish()
        }

        if (!mBluetoothAdapter?.isEnabled!!) {
            val turnOn = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(turnOn, 0);
            Toast.makeText(context, "BT Turned on",Toast.LENGTH_LONG).show();
        }
        val getVis = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        startActivityForResult(getVis, 0);

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

            val paired: Set<BluetoothDevice>? = mBluetoothAdapter?.bondedDevices;
            paired?.forEach { device ->
                val deviceName = device.name
                val deviceHardwareAddress = device.address // MAC address
            }

            mBluetoothList.clear()
            if (paired != null) {
                for (bt in paired) mBluetoothList.add(bt)
            }

            (mAdapter as ArrayAdapter<Any?>).notifyDataSetChanged()
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

            (mAdapter as ArrayAdapter<Any?>).notifyDataSetChanged()
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

        fragmentBtBinding.listView.setOnItemClickListener { parent, _, position, _ ->
            val device = parent.getItemAtPosition(position) as BluetoothDevice
            val name = device.name
            Log.d("Bluetooth", "Connecting to BT device: $name")

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