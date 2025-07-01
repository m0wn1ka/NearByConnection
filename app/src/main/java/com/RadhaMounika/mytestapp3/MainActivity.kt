package com.RadhaMounika.mytestapp3

import android.Manifest
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.nearby.connection.Strategy.P2P_CLUSTER
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    val AppCompatActivity.dataStore by preferencesDataStore(name = "UserMsgStore")
    private val userMsgKey = stringPreferencesKey("userMsg")
    private lateinit var  userMsg : String
    private lateinit var otherUserMsg : String
    private lateinit var connectionsClient: ConnectionsClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectionsClient = Nearby.getConnectionsClient(this)

        val textBox = findViewById<TextInputEditText>(R.id.editTextTextMultiLine)

        val updateMsgButton = findViewById<Button>(R.id.updateMsgButton)
        val advertiseButton = findViewById<Button>(R.id.buttonAdvertise)


        updateMsgButton.setOnClickListener {
            val msg = textBox.text.toString()
            lifecycleScope.launch {
                dataStore.edit { preferences ->
                    preferences[userMsgKey] = msg
                }
            }
        }

        advertiseButton.setOnClickListener {
            startAdvertising()
        }

        requestPermissions()
        startDiscovery()

        // Continuously observe DataStore
        lifecycleScope.launch {
            dataStore.data.collectLatest { preferences ->
                userMsg = preferences[userMsgKey] ?: "No message saved"

                textBox.setText(userMsg)//so now we set user msg intially
            }
        }
    }

    private fun requestPermissions() {
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.entries.any { !it.value }) {
                Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
            }
        }.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        )
    }

    private fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(P2P_CLUSTER).build()
        connectionsClient.startAdvertising(
            "Advertiser isss radha ",
            "com.example.nearbydemo.SERVICE_ID",
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            showToast("Advertising started")
        }.addOnFailureListener {
            showToast("F: ${it.message}")
        }
    }

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(P2P_CLUSTER).build()
        connectionsClient.startDiscovery(
            "com.example.nearbydemo.SERVICE_ID",
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            showToast("Discovery started")
        }.addOnFailureListener {
            showToast("F: ${it.message}")
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            showToast("connection life cycle call back called999")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    showToast("Connected to $endpointId")
                    val payload = Payload.fromBytes(userMsg.toByteArray())
                    connectionsClient.sendPayload(endpointId, payload)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    showToast("Connection rejected")
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    showToast("Connection error")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            showToast("Disconnected from $endpointId")
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            showToast("radha Ep found: ${info.endpointName}")
            connectionsClient.requestConnection(
                "Discoverer",
                endpointId,
                connectionLifecycleCallback
            )
        }

        override fun onEndpointLost(endpointId: String) {
            showToast("Endpoint lost: $endpointId")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                val message = String(bytes)
                otherUserMsg= message
                val msgTextView = findViewById<TextView>(R.id.savedMsgTextView)
                msgTextView.setText(message)
                showToast("Received: $message")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            showToast("payload transfer update:" + update.status)
        }
    }

    private fun showToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}
