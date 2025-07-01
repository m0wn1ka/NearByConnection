package com.RadhaMounika.mytestapp3

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
    //the below data store stores the user msg
    val AppCompatActivity.dataStore by preferencesDataStore(name = "UserMsgStore")
    private val userMsgKey = stringPreferencesKey("userMsg")
    private lateinit var  userMsg : String
    private lateinit var connectionsClient: ConnectionsClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectionsClient = Nearby.getConnectionsClient(this)

        val textBox = findViewById<TextInputEditText>(R.id.editTextTextMultiLine)//users msg
        val updateMsgButton = findViewById<Button>(R.id.updateMsgButton)//update button to save the user msg
        val advertiseButton = findViewById<Button>(R.id.buttonAdvertise)//the button to share (will call adverise)

        updateMsgButton.setOnClickListener {
            //on click of update msg button
            // get user msg and save it with pre defined key
            val msg = textBox.text.toString()
            lifecycleScope.launch {
                dataStore.edit { preferences ->
                    preferences[userMsgKey] = msg
                }
            }
        }

        advertiseButton.setOnClickListener {
            //on click of `the share msg button` we call this
            startAdvertising()
        }
        //we check for permissions on creation
        requestPermissions()
        //at the start of app opening itself we start discovering for other devices
        startDiscovery()
        lifecycleScope.launch {
            dataStore.data.collectLatest { preferences ->
                userMsg = preferences[userMsgKey] ?: "Test message by radha"
                //the user input will look empty on create
                //so we get it from data store and set it
                textBox.setText(userMsg)
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
                Manifest.permission.NEARBY_WIFI_DEVICES,
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
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(P2P_CLUSTER).build()//many to many
        val deviceName = Settings.Secure.getString(
            contentResolver,
            "bluetooth_name"
        ) ?: Build.MODEL
        val advertisingName = "Device: $deviceName"
        connectionsClient.startAdvertising(
            advertisingName,
            "com.RadhaMounika.nearby",//unique to each app
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
            "com.RadhaMounika.nearby",
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
//            accept the connection when inititated by other end
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    showToast("Connected to $endpointId")
                    val payload = Payload.fromBytes(userMsg.toByteArray())
                    connectionsClient.sendPayload(endpointId, payload)
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            connectionsClient.requestConnection(
                "Discoverer",
                endpointId,
                connectionLifecycleCallback
            )
        }

        override fun onEndpointLost(endpointId: String) {
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                val message = "Received Msg:"+String(bytes)
                val msgTextView = findViewById<TextView>(R.id.savedMsgTextView)
                msgTextView.setText(message)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
        }
    }

    private fun showToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
