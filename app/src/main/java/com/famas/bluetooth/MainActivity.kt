package com.famas.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.famas.bluetooth.ui.theme.BluetoothTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.*


val UUID_INSECURE: UUID =
    UUID.fromString("00001101-0000-1000-8000-00805f9b34fb") //fa87c0d0-afac-11de-8a39-0800200c9a66
val UUID_SECURE: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")


@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {

    private var loading by mutableStateOf(false)
    private var isBluetoothDisabled by mutableStateOf(false)
    private val availableBluetoothDevices = mutableStateListOf<BluetoothDevice>()
    private var scanning by mutableStateOf(false)
    private var isBluetoothStateChanged by mutableStateOf(false)
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter? = null

    private val TAG = "myTag"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothPermissionsRegister.launch(BLUETOOTH_PERMISSIONS)

        registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED))
        registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
        registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))


        // Check to see if the Bluetooth classic feature is available.
        packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH) }?.also {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
        }

        bluetoothManager = getSystemService(BluetoothManager::class.java) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Log.d(TAG, "device doesn't support bluetooth")
        }

        setContent {
            BluetoothTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column {
                        if (isBluetoothDisabled) {
                            Button(
                                onClick = { enableBluetoothIfNotEnabled() },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text(text = "Enable")
                            }
                        } else Text(
                            text = "Bluetooth enabled",
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(text = "Available bluetooth devices:")
                        Spacer(modifier = Modifier.height(20.dp))
                        if (scanning) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            if (isBluetoothStateChanged) {
                                items(availableBluetoothDevices) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "${item.name} " +
                                                when (item.bondState) {
                                                    BluetoothDevice.BOND_BONDING -> " - pairing"
                                                    BluetoothDevice.BOND_BONDED -> " - paired"
                                                    else -> {
                                                        ""
                                                    }
                                                }, modifier = Modifier
                                            .padding(8.dp)
                                            .clickable { item.createBond() }
                                        )

                                        if (item.bondState == BluetoothDevice.BOND_BONDED) {
                                            Button(onClick = {
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    ConnectThread(item).run()
                                                }
                                            }) {
                                                Text(text = "Connect")
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(availableBluetoothDevices) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "${item.name} " +
                                                when (item.bondState) {
                                                    BluetoothDevice.BOND_BONDING -> " - pairing"
                                                    BluetoothDevice.BOND_BONDED -> " - paired"
                                                    else -> {
                                                        ""
                                                    }
                                                }, modifier = Modifier
                                            .padding(8.dp)
                                            .clickable { item.createBond() }
                                        )

                                        if (item.bondState == BluetoothDevice.BOND_BONDED) {
                                            Button(onClick = {
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    ConnectThread(item).run()
                                                }
                                            }) {
                                                Text(text = "Connect")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { scanForDevices() },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(text = "Scan")
                        }
                    }
                }

                if (loading) {
                    Dialog(onDismissRequest = { /*TODO*/ }) {
                        Card {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
    }

    private inner class ConnectThread(val device: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(UUID_SECURE)
        }

        override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter?.cancelDiscovery()


            mmSocket?.let { socket ->
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                loading = true
                if (socket.isConnected) {
                    toastOnMainThread("Already connected")
                } else {
                    try {
                        socket.connect()
                        toastOnMainThread("Connected")
                    } catch (e: Exception) {
                        try {
                            Log.d(TAG, "trying fallback...")
                            device::class.java
                                .getMethod(
                                    "createRfcommSocket",
                                    Int::class.javaPrimitiveType
                                ).invoke(device, 1)
                            socket.connect()
                            Log.d(TAG, "Connected")
                        } catch (e2: java.lang.Exception) {
                            Log.d(TAG, "Couldn't establish Bluetooth connection!")
                        }
                        toastOnMainThread(e.localizedMessage ?: "Failed to connect")
                    }
                }
                loading = false
            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            loading = false
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }

    private fun scanForDevices() {
        if (bluetoothAdapter?.isDiscovering == true) return
        val returnedStartDiscovery = bluetoothAdapter?.startDiscovery()
        Log.d(TAG, "start discovery: $returnedStartDiscovery")
    }

    private fun enableBluetoothIfNotEnabled() {
        if (bluetoothAdapter?.isEnabled == false) {
            val bluetoothEnableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            registerToEnableBluetooth.launch(bluetoothEnableIntent)
        }
    }

    private val registerToEnableBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isBluetoothDisabled = bluetoothAdapter == null || bluetoothAdapter?.isEnabled == false
        }

    private fun PackageManager.missingSystemFeature(name: String): Boolean =
        !hasSystemFeature(name)

    private val bluetoothPermissionsRegister =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            Log.d(TAG, it.toString())
        }


    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "on receive: ${intent?.action}")
            val action = intent?.action
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (!availableBluetoothDevices.contains(device) && device != null) {
                        availableBluetoothDevices.add(device)
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    scanning = true
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    scanning = false
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    when (device?.bondState) {
                        BluetoothDevice.BOND_BONDED -> {
                            Log.d(TAG, "ACTION_BOND_STATE_CHANGED: BOND_BONDED")
                            isBluetoothStateChanged = !isBluetoothStateChanged
                        }

                        BluetoothDevice.BOND_BONDING -> {
                            Log.d(TAG, "ACTION_BOND_STATE_CHANGED: BOND_BONDING")
                            isBluetoothStateChanged = !isBluetoothStateChanged
                        }

                        BluetoothDevice.BOND_NONE -> {
                            Log.d(TAG, "ACTION_BOND_STATE_CHANGED: BOND_NONE")
                            isBluetoothStateChanged = !isBluetoothStateChanged
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isBluetoothDisabled = bluetoothAdapter == null || bluetoothAdapter?.isEnabled == false
        if (bluetoothAdapter?.isDiscovering == true) {
            return
        }
        val returnedStartDiscovery = bluetoothAdapter?.startDiscovery()
        Log.d(TAG, "$returnedStartDiscovery")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    @SuppressLint("MissingPermission")
    override fun onPause() {
        super.onPause()
        bluetoothAdapter?.cancelDiscovery()
    }

    private fun toastOnMainThread(s: String) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, s, Toast.LENGTH_LONG)
                .show()
        }
    }
}

val BLUETOOTH_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
    )
} else {
    arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
}