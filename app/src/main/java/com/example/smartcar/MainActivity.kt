package com.example.smartcar

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.example.smartcar.BluetoothManager as SmartCarBluetoothManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Handler

class MainActivity : AppCompatActivity() {
    private var isConnected = false
    val bluetoothManager: SmartCarBluetoothManager by lazy { SmartCarBluetoothManager(this) }
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var selectedDeviceAddress: String? = null

    // --- Pentru scanare și listare dispozitive ---
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private val discoveredDeviceNames = mutableListOf<String>()
    private var discoveryDialog: AlertDialog? = null
    private var isReceiverRegistered = false
    private var progressBar: ProgressBar? = null

    // Variabile pentru UI
    private var temperatureTextView: TextView? = null
    private var distanceTextView: TextView? = null
    private var lastDistance: Int? = null
    private var lastTemp: Float? = null
    private var lastDistanceTimestamp: Long = 0L
    private var lastTempTimestamp: Long = 0L
    private val distanceHandler = Handler()
    private val distanceRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (lastDistance != null && now - lastDistanceTimestamp < 10000) {
                distanceTextView?.text = "Distanță: $lastDistance cm"
            } else {
                distanceTextView?.text = "Distanță: hardware problem"
            }
            if (lastTemp != null && now - lastTempTimestamp < 10000) {
                temperatureTextView?.text = "Temperatură: $lastTemp°C"
            } else {
                temperatureTextView?.text = "Temperatură: hardware problem"
            }
            distanceHandler.postDelayed(this, 6000)
        }
    }

    private val discoveryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (discoveredDevices.none { d -> d.address == device.address }) {
                            discoveredDevices.add(device)
                            val name = device.name ?: "Unknown device"
                            discoveredDeviceNames.add("$name\n${device.address}")
                            // Actualizează lista din dialog
                            (discoveryDialog?.listView?.adapter as? android.widget.ArrayAdapter<*>)?.notifyDataSetChanged()
                            Toast.makeText(this@MainActivity, "Descoperit: $name (${device.address})", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    // Ascunde progressBar-ul
                    progressBar?.visibility = View.GONE
                    if (discoveredDevices.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Nu s-a găsit niciun dispozitiv Bluetooth!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1
        private val PERMISSIONS_BLUETOOTH = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inițializez referințele pentru noile TextView-uri
        temperatureTextView = findViewById(R.id.temperatureTextView)
        distanceTextView = findViewById(R.id.distanceTextView)
        distanceHandler.post(distanceRunnable)

        // Verifică și cere permisiunile Bluetooth
        if (!checkBluetoothPermissions()) {
            requestBluetoothPermissions()
        }

        setupBluetoothDataCallback()

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val connectButton = findViewById<MaterialButton>(R.id.connectButton)
        val connectionStatus = findViewById<ImageView>(R.id.connectionStatus)
        val connectionText = findViewById<TextView>(R.id.connectionText)

        // Create adapter for ViewPager2
        val adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Control"
                1 -> "Sensors"
                else -> null
            }
        }.attach()

        // Setup connect button
        connectButton.setOnClickListener {
            if (!isConnected) {
                showBluetoothDevicesDialog(connectButton, connectionStatus, connectionText)
            } else {
                disconnectFromArduino(connectButton, connectionStatus, connectionText)
            }
        }
    }

    private fun setupBluetoothDataCallback() {
        bluetoothManager.setDataCallback { data ->
            // Procesează datele primite de la Arduino
            // Exemplu: "RPM: 440 Temp: 25.5C Battery: 100% Dist: 123cm"
            val parts = data.split(" ")
            try {
                var rpm: Int? = null
                var temp: Float? = null
                var battery: Int? = null
                var dist: Int? = null
                for (i in parts.indices) {
                    when (parts[i]) {
                        "RPM:" -> rpm = parts.getOrNull(i + 1)?.toIntOrNull()
                        "Temp:" -> temp = parts.getOrNull(i + 1)?.replace("C", "")?.toFloatOrNull()
                        "Battery:" -> battery = parts.getOrNull(i + 1)?.replace("%", "")?.toIntOrNull()
                        "Dist:" -> dist = parts.getOrNull(i + 1)?.replace("cm", "")?.toIntOrNull()
                    }
                }
                // Actualizează UI-ul cu noile valori
                runOnUiThread {
                    updateParameters(rpm, temp, battery, dist)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error parsing Arduino data", e)
            }
        }
    }

    private fun showBluetoothDevicesDialog(connectButton: MaterialButton, connectionStatus: ImageView, connectionText: TextView) {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth nu este suportat pe acest dispozitiv", Toast.LENGTH_SHORT).show()
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            AlertDialog.Builder(this)
                .setTitle("Bluetooth dezactivat")
                .setMessage("Trebuie să activezi Bluetooth pentru a căuta dispozitive. Vrei să deschizi setările Bluetooth?")
                .setPositiveButton("Deschide setări") { _, _ ->
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    startActivity(intent)
                }
                .setNegativeButton("Anulează", null)
                .show()
            return
        }

        // Verifică permisiunile înainte de scanare
        if (!checkBluetoothPermissions()) {
            AlertDialog.Builder(this)
                .setTitle("Permisiuni necesare")
                .setMessage("Trebuie să acorzi permisiuni pentru Bluetooth și Locație. Vrei să deschizi setările aplicației?")
                .setPositiveButton("Deschide setări") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = android.net.Uri.fromParts("package", packageName, null)
                    startActivity(intent)
                }
                .setNegativeButton("Anulează", null)
                .show()
            requestBluetoothPermissions()
            return
        }

        // Golește lista de fiecare dată
        discoveredDevices.clear()
        discoveredDeviceNames.clear()

        // Adaugă dispozitivele împerecheate la început
        val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
        for (device in pairedDevices) {
            discoveredDevices.add(device)
            val name = device.name ?: "Unknown device"
            discoveredDeviceNames.add("$name\n${device.address}")
        }

        // Adapter pentru dialog
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_list_item_1, discoveredDeviceNames)

        // Inflate custom view cu ProgressBar
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(android.R.layout.select_dialog_item, null)
        progressBar = ProgressBar(this)
        progressBar?.isIndeterminate = true
        progressBar?.visibility = View.VISIBLE

        // Dialogul de selecție
        val builder = AlertDialog.Builder(this)
            .setTitle("Alege dispozitivul Bluetooth")
            .setAdapter(adapter) { _, which ->
                selectedDeviceAddress = discoveredDevices[which].address
                bluetoothAdapter.cancelDiscovery()
                discoveryDialog?.dismiss()
                connectToArduino(connectButton, connectionStatus, connectionText, selectedDeviceAddress!!)
            }
            .setNegativeButton("Anulează") { _, _ ->
                bluetoothAdapter.cancelDiscovery()
            }
            .setOnDismissListener {
                bluetoothAdapter.cancelDiscovery()
            }
            .setView(progressBar)
            .setNeutralButton("Re-scanare") { _, _ ->
                bluetoothAdapter.cancelDiscovery()
                showBluetoothDevicesDialog(connectButton, connectionStatus, connectionText)
            }

        discoveryDialog = builder.show()

        // Pornește scanarea
        val started = bluetoothAdapter.startDiscovery()
        Toast.makeText(this, "Scanare pornită: $started", Toast.LENGTH_SHORT).show()
        progressBar?.visibility = if (started) View.VISIBLE else View.GONE

        // Înregistrează receiver-ul dacă nu e deja
        if (!isReceiverRegistered) {
            val filter = android.content.IntentFilter()
            filter.addAction(BluetoothDevice.ACTION_FOUND)
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            registerReceiver(discoveryReceiver, filter)
            isReceiverRegistered = true
        }

        // Dacă nu există niciun dispozitiv după scanare, arată mesaj
        discoveryDialog?.setOnDismissListener {
            bluetoothAdapter.cancelDiscovery()
            if (discoveredDevices.isEmpty()) {
                Toast.makeText(this, "Nu s-a găsit niciun dispozitiv Bluetooth!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun connectToArduino(connectButton: MaterialButton, connectionStatus: ImageView, connectionText: TextView, deviceAddress: String) {
        bluetoothManager.connectToDevice(deviceAddress) { success ->
            runOnUiThread {
                if (success) {
                    isConnected = true
                    connectionStatus.setImageResource(android.R.drawable.presence_online)
                    connectionStatus.setColorFilter(resources.getColor(R.color.success, theme))
                    connectionText.text = "Connected to Arduino"
                    connectButton.text = "Disconnect"
                    Toast.makeText(this, "Connected to Arduino", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to connect to Arduino", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun disconnectFromArduino(connectButton: MaterialButton, connectionStatus: ImageView, connectionText: TextView) {
        bluetoothManager.disconnect()
        isConnected = false
        connectionStatus.setImageResource(android.R.drawable.presence_offline)
        connectionStatus.setColorFilter(resources.getColor(R.color.accent, theme))
        connectionText.text = "Disconnected"
        connectButton.text = "Connect to Arduino"
        Toast.makeText(this, "Disconnected from Arduino", Toast.LENGTH_SHORT).show()
    }

    private fun checkBluetoothPermissions(): Boolean {
        return PERMISSIONS_BLUETOOTH.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsLauncher.launch(PERMISSIONS_BLUETOOTH)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_BLUETOOTH_PERMISSIONS
            )
        }
    }

    // Launcher pentru cerere permisiuni multiple
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Permisiunile Bluetooth și Locație sunt necesare pentru scanare!", Toast.LENGTH_LONG).show()
            // Deschide setările aplicației dacă utilizatorul refuză
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.fromParts("package", packageName, null)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Permisiuni acordate!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isConnected) {
            bluetoothManager.disconnect()
        }
        if (isReceiverRegistered) {
            unregisterReceiver(discoveryReceiver)
            isReceiverRegistered = false
        }
        distanceHandler.removeCallbacks(distanceRunnable)
    }

    // Suprascriu funcția updateParameters pentru a afișa și temperatura și distanța
    private fun updateParameters(rpm: Int?, temp: Float?, battery: Int?, dist: Int?) {
        if (temp != null) {
            lastTemp = temp
            lastTempTimestamp = System.currentTimeMillis()
        }
        if (dist != null) {
            lastDistance = dist
            lastDistanceTimestamp = System.currentTimeMillis()
        }
        // Distanța și temperatura se actualizează la fiecare 6 secunde din handler
        // Poți adăuga și alte actualizări pentru RPM, baterie etc. dacă vrei
    }
} 