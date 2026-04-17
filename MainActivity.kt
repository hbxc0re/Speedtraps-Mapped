package com.corelabs.speedtrapsmapped

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.corelabs.speedtrapsmapped.data.ESPManager
import com.corelabs.speedtrapsmapped.data.SpeedtrapAlert
import com.corelabs.speedtrapsmapped.ui.MapScreen
import com.corelabs.speedtrapsmapped.ui.theme.SpeedtrapsMappedTheme
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng

class MainActivity : ComponentActivity(), ESPManager.ESPDelegate {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var espManager: ESPManager

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    // FIX 1: Continuous location updates instead of one-shot lastLocation
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                val latLng = LatLng(location.latitude, location.longitude)
                viewModel.updateLocation(latLng)
                espManager.updateLocation(latLng)   // kept in sync on every update
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            result.device?.let { device ->
                try {
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        if (device.name?.contains("V1", ignoreCase = true) == true) {
                            viewModel.addDiscoveredDevice(device)
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e("MainActivity", "Bluetooth permission missing during scan result", e)
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startLocationUpdates()
        }
        // FIX 2: Correctly gate BLE scan on the right permission per API level
        val scanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true
        } else {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }
        if (scanPermission) {
            startBluetoothDiscovery()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        espManager = ESPManager.getInstance(this)
        espManager.setDelegate(this)

        checkPermissions()

        setContent {
            SpeedtrapsMappedTheme {
                val currentLocation by viewModel.currentLocation.collectAsState()
                val alerts = viewModel.alerts
                val discoveredDevices = viewModel.discoveredDevices
                val isConnected by viewModel.isConnected.collectAsState()

                var showDevicePicker by remember { mutableStateOf(false) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    floatingActionButton = {
                        FloatingActionButton(onClick = {
                            showDevicePicker = true
                            startBluetoothDiscovery()
                        }) {
                            Text(if (isConnected) "V1 Connected" else "Connect V1")
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        MapScreen(
                            currentLocation = currentLocation,
                            alerts = alerts
                        )

                        if (showDevicePicker) {
                            DevicePickerDialog(
                                devices = discoveredDevices,
                                onDeviceSelected = { device ->
                                    viewModel.setConnecting(true)
                                    espManager.connect(device)
                                    showDevicePicker = false
                                    stopBluetoothDiscovery()
                                },
                                onDismiss = {
                                    showDevicePicker = false
                                    stopBluetoothDiscovery()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // FIX 3: Stop scanner and location updates to prevent leaks
    override fun onDestroy() {
        super.onDestroy()
        stopBluetoothDiscovery()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun startBluetoothDiscovery() {
        // FIX 4: Don't request legacy BLUETOOTH/BLUETOOTH_ADMIN on API 31+;
        //        gate purely on the correct permission for the running API level.
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        viewModel.clearDiscoveredDevices()
        bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
    }

    private fun stopBluetoothDiscovery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private fun checkPermissions() {
        // FIX 5: Only request legacy Bluetooth permissions below API 31.
        //        On API 31+ use BLUETOOTH_SCAN and BLUETOOTH_CONNECT instead.
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            @Suppress("DEPRECATION")
            permissions.add(Manifest.permission.BLUETOOTH)
            @Suppress("DEPRECATION")
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startLocationUpdates()
            startBluetoothDiscovery()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    // FIX 6: Use continuous LocationRequest instead of one-shot lastLocation
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateDistanceMeters(5f)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            Log.e("MainActivity", "Location permission missing when starting updates")
        }
    }

    override fun onAlertTableReceived(alerts: List<SpeedtrapAlert>) {
        alerts.forEach { viewModel.addAlert(it) }
    }

    // FIX 7: Clear the "connecting" flag whenever connection state changes
    override fun onConnectionStatusChanged(isConnected: Boolean) {
        viewModel.setConnecting(false)
        viewModel.setConnected(isConnected)
    }
}

@Composable
fun DevicePickerDialog(
    devices: List<BluetoothDevice>,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onDismiss: () -> Unit
) {
    // FIX 8: Resolve device display names once outside the composable loop so
    //        permission checks don't run on every recomposition of each item.
    val context = androidx.compose.ui.platform.LocalContext.current
    val connectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Manifest.permission.BLUETOOTH_CONNECT
    } else {
        @Suppress("DEPRECATION")
        Manifest.permission.BLUETOOTH
    }
    val hasConnectPermission = remember(context) {
        ActivityCompat.checkSelfPermission(context, connectPermission) ==
                PackageManager.PERMISSION_GRANTED
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select V1 Device") },
        text = {
            LazyColumn {
                items(devices) { device ->
                    val displayName = remember(device.address) {
                        if (hasConnectPermission) device.name ?: "Unknown Device"
                        else "Permission Required"
                    }
                    ListItem(
                        headlineContent = { Text(displayName) },
                        supportingContent = { Text(device.address) },
                        modifier = Modifier.clickable { onDeviceSelected(device) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}