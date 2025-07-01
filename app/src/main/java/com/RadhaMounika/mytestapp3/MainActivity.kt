package com.RadhaMounika.mytestapp3

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private lateinit var userMsg: String
    private lateinit var connectionsClient: ConnectionsClient
    private var permissionsGranted = false

    // Store the permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            permissionsGranted = true
            showToast("Permissions granted")
            // Start discovery only after permissions are granted
            startDiscovery()
        } else {
            permissionsGranted = false
            showToast("App needs all permissions to work properly")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectionsClient = Nearby.getConnectionsClient(this)

        val textBox = findViewById<TextInputEditText>(R.id.editTextTextMultiLine)//users msg
        val updateMsgButton = findViewById<Button>(R.id.updateMsgButton)//update button to save the user msg
        val advertiseButton = findViewById<Button>(R.id.buttonAdvertise)//the button to share (will call adverise)

        updateMsgButton.setOnClickListener {
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
        val requiredPermissions = mutableListOf<String>().apply {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
                add(Manifest.permission.ACCESS_WIFI_STATE)
                add(Manifest.permission.CHANGE_WIFI_STATE)
            }
        }

        // Check if permissions are already granted
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            permissionsGranted = true
            startDiscovery()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startAdvertising() {
        if (!permissionsGranted) {
            showToast("Permissions not granted")
            return
        }

        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(P2P_CLUSTER).build()
        val deviceName = try {
            Settings.Secure.getString(contentResolver, "bluetooth_name") ?: Build.MODEL
        } catch (e: Exception) {
            Build.MODEL
        }
        val advertisingName = "Device: $deviceName"

        connectionsClient.startAdvertising(
            advertisingName,
            "com.RadhaMounika.nearby",//unique to each app
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            showToast("Advertising started")
        }.addOnFailureListener {
            showToast("Advertising failed: ${it.message}")
        }
    }

    private fun startDiscovery() {
        if (!permissionsGranted) {
            return
        }

        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(P2P_CLUSTER).build()
        connectionsClient.startDiscovery(
            "com.RadhaMounika.nearby",
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            showToast("Discovery started")
        }.addOnFailureListener {
            showToast("Discovery failed: ${it.message}")
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    showToast("Connected to $endpointId")
                    val payload = Payload.fromBytes(userMsg.toByteArray())
                    connectionsClient.sendPayload(endpointId, payload)
                }
                else -> {
                    showToast("Connection failed: ${result.status.statusMessage}")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            showToast("Disconnected from $endpointId")
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            showToast("Found device: ${info.endpointName}")
            connectionsClient.requestConnection(
                "Discoverer",
                endpointId,
                connectionLifecycleCallback
            )
        }

        override fun onEndpointLost(endpointId: String) {
            showToast("Lost device: $endpointId")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                val message = "Received Msg: ${String(bytes)}"
                val msgTextView = findViewById<TextView>(R.id.savedMsgTextView)
                msgTextView.text = message
                showToast(message)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Handle transfer updates if needed
        }
    }

    private fun showToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}