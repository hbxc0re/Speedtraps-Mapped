package com.corelabs.speedtrapsmapped

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateListOf
import com.corelabs.speedtrapsmapped.data.SpeedtrapAlert
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {
    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation = _currentLocation.asStateFlow()

    private val _alerts = mutableStateListOf<SpeedtrapAlert>()
    val alerts: List<SpeedtrapAlert> = _alerts

    private val _discoveredDevices = mutableStateListOf<BluetoothDevice>()
    val discoveredDevices: List<BluetoothDevice> = _discoveredDevices

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting = _isConnecting.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    fun updateLocation(latLng: LatLng) {
        _currentLocation.value = latLng
    }

    fun addAlert(alert: SpeedtrapAlert) {
        _alerts.add(alert)
    }

    fun addDiscoveredDevice(device: BluetoothDevice) {
        if (!_discoveredDevices.any { it.address == device.address }) {
            _discoveredDevices.add(device)
        }
    }

    fun clearDiscoveredDevices() {
        _discoveredDevices.clear()
    }

    fun setConnecting(connecting: Boolean) {
        _isConnecting.value = connecting
    }

    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
        if (connected) _isConnecting.value = false
    }
}
