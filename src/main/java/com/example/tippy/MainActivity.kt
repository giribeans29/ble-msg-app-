package com.example.tippy

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.tippy.ui.theme.TippyTheme
import org.json.JSONObject
import java.util.*

data class ChatMessage(
    val text: String,
    val sender: String,
    val timestamp: Long = System.currentTimeMillis()
)
class MainActivity : ComponentActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanCallback: ScanCallback? = null

    private var bluetoothGatt: BluetoothGatt? = null
    private var gattServer: BluetoothGattServer? = null
    private val connectedDevices = mutableStateListOf<BluetoothDevice>()

    // UUIDs for our custom service and characteristic (Must match on both devices)
    private val serviceUuid = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
    private val charUuid = UUID.fromString("0000abcd-0000-1000-8000-00805f9b34fb")
    private val cccDescriptorUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Reactive states for UI
    private var connectionStatus by mutableStateOf("Disconnected")
    private val chatMessages = mutableStateListOf<ChatMessage>()
    private val seenMessage = mutableSetOf<String>()
    private var isScanning by mutableStateOf(false)
    private var isAdvertising by mutableStateOf(false)
    private var isServerRole by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
        scanner = bluetoothAdapter?.bluetoothLeScanner
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser

        setContent {
            TippyTheme {
                val context = LocalContext.current
                val devices = remember { mutableStateListOf<BluetoothDevice>() }
                var messageToSend by remember { mutableStateOf("") }

                // Permission Handling
                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                } else {
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                }

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    if (result.values.all { it }) {
                        Log.d("BLE", "All permissions granted")
                    } else {
                        Log.e("BLE", "Permissions denied")
                    }
                }

                LaunchedEffect(Unit) {
                    val missing = permissions.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) {
                        launcher.launch(permissions)
                    }
                }

                Scaffold(
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        CenterAlignedTopAppBar(
                            title = { Text("BLE Chat & Scan", fontWeight = FontWeight.Bold) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Server Status Section
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Server Mode", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Status: $connectionStatus")
                                Row(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { startServerAndAdvertise() },
                                        enabled = !isAdvertising
                                    ) {
                                        Text(if (isAdvertising) "Advertising..." else "Start Server")
                                    }
                                    if (isAdvertising) {
                                        Spacer(Modifier.width(8.dp))
                                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text("Available Devices", fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(onClick = {
                                if (isScanning) stopScan() else {
                                    devices.clear()
                                    startScan { device ->
                                        if (devices.none { it.address == device.address }) {
                                            devices.add(device)
                                        }
                                    }
                                }
                            }) {
                                Text(if (isScanning) "Stop Scan" else "Scan Devices")
                            }
                            if (isScanning) {
                                Spacer(Modifier.width(8.dp))
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            items(items = devices) { device ->
                                DeviceListItem(device) { connectToDevice(it) }
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("Conversation", fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(8.dp)
                        ) {
                            val listState = rememberLazyListState()
                            LaunchedEffect(chatMessages.size) {
                                if (chatMessages.isNotEmpty()) {
                                    listState.animateScrollToItem(chatMessages.size - 1)
                                }
                            }

                            LazyColumn(state = listState) {
                                items(items = chatMessages) { msg ->
                                    ChatBubble(msg)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = messageToSend,
                                onValueChange = { messageToSend = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Type a message") },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            FloatingActionButton(
                                onClick = {
                                    if (messageToSend.isNotBlank()) {
                                        sendMessage(messageToSend)
                                        messageToSend = ""
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ChatBubble(message: ChatMessage) {
        val isMe = message.sender == "Me"
        val alignment = if (isMe) Alignment.End else Alignment.Start
        val bgColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
        val textColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = alignment
        ) {
            Surface(
                color = bgColor,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 0.dp,
                    bottomEnd = if (isMe) 0.dp else 16.dp
                ),
                tonalElevation = 2.dp
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    @Composable
    fun DeviceListItem(device: BluetoothDevice, onClick: (BluetoothDevice) -> Unit) {
        val context = LocalContext.current
        val name = try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                device.name ?: "Unknown Device"
            } else {
                "Permission Required"
            }
        } catch (_ : SecurityException) {
            "Unknown Device"
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(device) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, fontWeight = FontWeight.Medium)
                Text(device.address, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }


    private fun startScan(onDeviceFound: (BluetoothDevice) -> Unit) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return

        isScanning = true
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onDeviceFound(result.device)
            }
            override fun onScanFailed(errorCode: Int) {
                Log.e("BLE", "Scan Failed: $errorCode")
                isScanning = false
            }
        }

        scanner?.startScan(scanCallback)
        
        // Auto-stop scan after 10 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            if (isScanning) stopScan()
        }, 10000)
    }

    private fun createJsonMessage(text: String): String {
        val senderName = if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter?.name ?: "Unknown"
        } else {
            "Unknown"
        }
        return JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("sender", senderName)
            put("text", text)
            put("ttl", 3)
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun forwardMessage(msg: String) {
        try{
            val parsed = JSONObject(msg)
            val id = parsed.optString(("id"), ( ""))
            var ttl = parsed.optInt("ttl", 0)

            if(ttl <= 0){
                Log.d("MESH", "Packet expired: $id")
                return
            }
            ttl--
            parsed.put("ttl",ttl)
            val updatedMsg = parsed.toString()
            val bytes = updatedMsg.toByteArray()

            Log.d("MESH", "Forwarding packet $id with TTL $ttl")

            if(isServerRole){
                val service = gattServer?.getService(serviceUuid)
                val characteristic = service?.getCharacteristic(charUuid)
                
                if (characteristic != null) {
                    for(device in connectedDevices){
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gattServer?.notifyCharacteristicChanged(device, characteristic, false, bytes)
                        } else {
                            @Suppress("DEPRECATION")
                            characteristic.value = bytes
                            @Suppress("DEPRECATION")
                            gattServer?.notifyCharacteristicChanged(device, characteristic, false)
                        }
                    }
                }
            } else {
                val service = bluetoothGatt?.getService(serviceUuid)
                val characteristic = service?.getCharacteristic(charUuid)
                
                if (characteristic != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        bluetoothGatt?.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    } else {
                        @Suppress("DEPRECATION")
                        characteristic.value = bytes
                        @Suppress("DEPRECATION")
                        bluetoothGatt?.writeCharacteristic(characteristic)
                    }
                }
            }
        } catch (e: Exception){
            Log.e("MESH","Forward Failed: ${e.message}")
        }
    }

    private fun stopScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            scanner?.stopScan(scanCallback)
        }
        isScanning = false
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        
        isServerRole = false
        connectionStatus = "Connecting to ${device.address}..."
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            runOnUiThread {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connectionStatus = "Connected to ${gatt.device.address}"
                    if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gatt.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectionStatus = "Disconnected"
                    bluetoothGatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "Services discovered")
                // Enable notifications on the characteristic
                val service = gatt.getService(serviceUuid)
                val characteristic = service?.getCharacteristic(charUuid)
                if (characteristic != null) {
                    if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gatt.setCharacteristicNotification(characteristic, true)
                        val descriptor = characteristic.getDescriptor(cccDescriptorUuid)
                        if (descriptor != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            } else {
                                @Suppress("DEPRECATION")
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                @Suppress("DEPRECATION")
                                gatt.writeDescriptor(descriptor)
                            }
                        }
                    }
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val msg = String(value)
            val parsed = JSONObject(msg)
            val id = parsed.optString("id","")
            val text = parsed.optString("text","")
            val sender = parsed.optString("sender","")

            Log.d("MESH", "Received packet: $msg")
            if (seenMessage.contains(id)) {
                Log.d("MESH", "Duplicate ignored: $id")
                return
            }
            seenMessage.add(id)
            chatMessages.add(ChatMessage(text, sender))
            val ttl = parsed.optInt("ttl",0)
            if (ttl <= 0) return
            forwardMessage(parsed.toString())
        }
    }


    private fun startServerAndAdvertise() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) return

        isServerRole = true
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        gattServer = manager.openGattServer(this, serverCallback)

        val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            charUuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        )
        val descriptor = BluetoothGattDescriptor(
            cccDescriptorUuid,
            BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
        )
        characteristic.addDescriptor(descriptor)

        service.addCharacteristic(characteristic)
        gattServer?.addService(service)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(serviceUuid))
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d("BLE", "Advertising started")
            isAdvertising = true
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e("BLE", "Advertising failed: $errorCode")
            isAdvertising = false
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            runOnUiThread {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connectionStatus = "Client connected: ${device.address}"
                    if (!connectedDevices.contains(device)) {
                        connectedDevices.add(device)
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectionStatus = "Client disconnected"
                    connectedDevices.remove(device)
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            val msg = String(value)
            val parsed = JSONObject(msg)
            val id = parsed.optString("id","")
            val text = parsed.optString("text","")
            val sender = parsed.optString("sender","")

            if (seenMessage.contains(id)) return
            seenMessage.add(id)


            val ttl = parsed.optInt("ttl", 0)
            if(ttl <= 0) return
            if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                forwardMessage(parsed.toString())
            }

            Log.d("JSON", msg)
            Log.d("BLE", "Received: $msg")
            runOnUiThread {
                chatMessages.add(ChatMessage(text, sender))
            }
            
            if (responseNeeded) {
                if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (descriptor.uuid == cccDescriptorUuid) {
                if (responseNeeded) {
                    if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    }
                }
            }
        }
    }
    private fun sendMessage(message: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        if (isServerRole) {
            // Server: Notify all connected clients
            val service = gattServer?.getService(serviceUuid)
            val characteristic = service?.getCharacteristic(charUuid)
            if (characteristic != null) {
                val jsonMessage = createJsonMessage(message)
                val id = JSONObject(jsonMessage).optString("id")
                seenMessage.add(id)
                for (device in connectedDevices) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gattServer?.notifyCharacteristicChanged(device, characteristic, false, jsonMessage.toByteArray())
                    } else {
                        @Suppress("DEPRECATION")
                        characteristic.value = jsonMessage.toByteArray()
                        @Suppress("DEPRECATION")
                        gattServer?.notifyCharacteristicChanged(device, characteristic, false)
                    }
                }
                chatMessages.add(ChatMessage(message, "Me"))
            }
        } else {
            // Client: Write to server's characteristic
            val service = bluetoothGatt?.getService(serviceUuid)
            val characteristic = service?.getCharacteristic(charUuid)
            val jsonMessage = createJsonMessage(message)
            val id = JSONObject(jsonMessage).optString("id")
            seenMessage.add(id)
            if (characteristic != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt?.writeCharacteristic(characteristic,
                        jsonMessage.toByteArray(),
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = jsonMessage.toByteArray()
                    @Suppress("DEPRECATION")
                    bluetoothGatt?.writeCharacteristic(characteristic)
                }
                Log.d("MESH", "Sending: $jsonMessage")
                chatMessages.add(ChatMessage(message, "Me"))
            } else {
                Log.e("BLE", "Characteristic not found. Are you connected to a server?")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt?.close()
            gattServer?.close()
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
            advertiser?.stopAdvertising(advertiseCallback)
        }
    }
}
