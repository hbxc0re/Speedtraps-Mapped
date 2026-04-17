package com.corelabs.speedtrapsmapped.data

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.esplibrary.bluetooth.ConnectionEvent
import com.esplibrary.bluetooth.ConnectionType
import com.esplibrary.client.ESPClientListener
import com.esplibrary.client.IESPClient
import com.esplibrary.constants.DeviceId
import com.esplibrary.data.AlertData
import com.esplibrary.packets.ESPPacket
import com.esplibrary.packets.InfDisplayData
import com.google.android.gms.maps.model.LatLng

class ESPManager private constructor(private val context: Context) : ESPClientListener {

    interface ESPDelegate {
        fun onAlertTableReceived(alerts: List<SpeedtrapAlert>)
        fun onConnectionStatusChanged(isConnected: Boolean)
    }

    private var delegate: ESPDelegate? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var espClient: IESPClient? = null
    private var lastLocation: LatLng? = null

    companion object {
        private const val NO_DATA_TIMEOUT = 10000L

        @Volatile
        private var INSTANCE: ESPManager? = null

        fun getInstance(context: Context): ESPManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ESPManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun setDelegate(delegate: ESPDelegate) {
        this.delegate = delegate
    }

    fun updateLocation(location: LatLng) {
        this.lastLocation = location
    }

    fun connect(device: BluetoothDevice, connectionType: ConnectionType = ConnectionType.LE) {
        if (espClient != null && espClient!!.isConnected) {
            return
        }
        
        espClient = IESPClient.getClient(context, this, connectionType, NO_DATA_TIMEOUT)
        espClient?.connect(device, connectionType, null)
    }

    fun disconnect() {
        espClient?.disconnect()
    }

    // region ESPClientListener Implementation
    override fun onConnectionEvent(event: ConnectionEvent) {
        mainHandler.post {
            delegate?.onConnectionStatusChanged(event == ConnectionEvent.Connected)
        }
    }

    override fun onDisplayData(displayData: InfDisplayData) {
        // Handle physical display updates if needed
    }

    override fun onAlertTableReceived(alerts: List<AlertData>) {
        val currentLocation = lastLocation ?: return
        
        val mappedAlerts = alerts.map { alert ->
            SpeedtrapAlert(
                id = alert.index.toString(),
                location = currentLocation,
                band = alert.band.name,
                frequency = alert.frequency.toDouble() / 1000.0,
                signalStrength = alert.signalStrength
            )
        }
        
        mainHandler.post {
            delegate?.onAlertTableReceived(mappedAlerts)
        }
    }

    override fun onBasePacketReceived(packet: ESPPacket) {
        // Handle raw packets
    }

    override fun onNoData() {
        Log.w("ESPManager", "No data received from V1")
    }
    // endregion

    fun startMockAlerts(currentLocation: LatLng?) {
        if (currentLocation == null) return
        
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                val mockAlert = SpeedtrapAlert(
                    id = System.currentTimeMillis().toString(),
                    location = LatLng(
                        currentLocation.latitude + (Math.random() - 0.5) * 0.01,
                        currentLocation.longitude + (Math.random() - 0.5) * 0.01
                    ),
                    band = listOf("Ka", "K", "X", "Laser").random(),
                    frequency = 34.7 + Math.random(),
                    signalStrength = (1..8).random()
                )
                delegate?.onAlertTableReceived(listOf(mockAlert))
                mainHandler.postDelayed(this, 10000)
            }
        }, 2000)
    }
}
