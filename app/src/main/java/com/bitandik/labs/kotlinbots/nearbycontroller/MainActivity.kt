package com.bitandik.labs.kotlinbots.nearbycontroller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.nearby.connection.Strategy.P2P_STAR
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity(),
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private lateinit var googleApiClient: GoogleApiClient
    private var otherEndpointId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        googleApiClient = GoogleApiClient.Builder(this)
                                         .addApi(Nearby.CONNECTIONS_API)
                                         .addConnectionCallbacks(this)
                                         .addOnConnectionFailedListener(this)
                                         .build()

        googleApiClient.connect()

        disablePadLayout()
        btnFwd.setOnClickListener { sendMessage(FWD) }
        btnStop.setOnClickListener { sendMessage(STOP) }
        btnBack.setOnClickListener { sendMessage(BACK) }
        btnLeft.setOnClickListener { sendMessage(LEFT) }
        btnRight.setOnClickListener { sendMessage(RIGHT) }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (googleApiClient.isConnected) {
            if (otherEndpointId.isEmpty()) {
                Nearby.Connections.disconnectFromEndpoint(googleApiClient, otherEndpointId)
            } else {
                Nearby.Connections.stopDiscovery(googleApiClient)
            }
            googleApiClient.disconnect()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startDiscovery()
                } else {
                    Toast.makeText(this, "Permission is needed", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
    }

    //[ConnectionCallbacks]
    override fun onConnected(bundle: Bundle?) {
        if (checkLocationPermission()) {
            startDiscovery()
        }
    }

    override fun onConnectionSuspended(p0: Int) {}
    //[/ConnectionCallbacks]

    //[OnConnectionFailedListener]
    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        updateStatus("Connection failed ${connectionResult.errorMessage}")
    }
    //[//OnConnectionFailedListener]

    private fun startDiscovery() {
        updateStatus("Starting discovery")
        Nearby.Connections.startDiscovery(googleApiClient,
                NEARBY_SERVICE_ID,
                endpointDiscoveryCallback,
                DiscoveryOptions(P2P_STAR))
                .setResultCallback({ status ->
                    if (status.isSuccess) {
                        updateStatus("Discovery succeeded, waiting for endpoint")
                    }
                })
    }

    private fun checkLocationPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                        MY_PERMISSIONS_REQUEST_LOCATION)
            false
        } else {
            true
        }
    }

    private fun sendMessage(direction: String) {
        if (googleApiClient.isConnected) {
            Log.d(TAG, "Sending msg $direction to $otherEndpointId")
            Nearby.Connections.sendPayload(googleApiClient,
                                           otherEndpointId,
                                           Payload.fromBytes(direction.toByteArray()))
                                .setResultCallback({ status ->
                                    if (!status.isSuccess) {
                                        Nearby.Connections.disconnectFromEndpoint(googleApiClient, otherEndpointId)
                                    }
                                })
        }
    }

    private fun enablePadLayout() {
        btnFwd.visibility = View.VISIBLE
        btnStop.visibility = View.VISIBLE
        btnBack.visibility = View.VISIBLE
        btnLeft.visibility = View.VISIBLE
        btnRight.visibility = View.VISIBLE

        txtStatus.visibility = View.GONE
    }

    private fun disablePadLayout() {
        btnFwd.visibility = View.GONE
        btnStop.visibility = View.GONE
        btnBack.visibility = View.GONE
        btnLeft.visibility = View.GONE
        btnRight.visibility = View.GONE

        txtStatus.visibility = View.VISIBLE
    }

    private fun updateStatus(statusMessage: String) {
        txtStatus.text = "${txtStatus.text}\n$statusMessage"
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String,
                                     discoveredEndpointInfo: DiscoveredEndpointInfo) {

            updateStatus("End point found $endpointId\nRequesting connection")

            Nearby.Connections
                    .requestConnection(googleApiClient, null, endpointId, connectionLifecycleCallback)
                    .setResultCallback {
                        status ->
                        if (status.isSuccess) {
                            updateStatus("Connected ${status.statusCode}")
                        } else {
                            updateStatus("Connection failed")
                        }
                    }
        }

        override fun onEndpointLost(endpointId: String) {}
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(p0: String?, p1: Payload?) {}

        override fun onPayloadTransferUpdate(p0: String?, p1: PayloadTransferUpdate?) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {

        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            updateStatus("Connection initiated with $endpointId")
            Nearby.Connections.acceptConnection(googleApiClient, endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connected to $endpointId")
                    Nearby.Connections.stopDiscovery(googleApiClient)
                    otherEndpointId = endpointId
                    enablePadLayout()
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    updateStatus("Connection rejected")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            disablePadLayout()
            txtStatus.text = ""
            updateStatus("Disconnected from $endpointId\nReconnecting...")
            startDiscovery()
        }
    }

    companion object {
        val TAG = "kotlinbots"
        const val NEARBY_SERVICE_ID = "com.bitandik.labs.kotlinbots"
        const val MY_PERMISSIONS_REQUEST_LOCATION = 99
        const val FWD = "fwd"
        const val BACK = "back"
        const val LEFT = "left"
        const val RIGHT = "right"
        const val STOP = "stop"
    }
}
