package com.example.smartcar

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var _isConnected = false
    private val handler = Handler(Looper.getMainLooper())
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var dataCallback: ((String) -> Unit)? = null

    val isConnected: Boolean
        get() = _isConnected

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(deviceAddress: String, callback: (Boolean) -> Unit) {
        if (bluetoothAdapter == null) {
            callback(false)
            return
        }

        try {
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothSocket?.connect()
            
            outputStream = bluetoothSocket?.outputStream
            inputStream = bluetoothSocket?.inputStream
            
            _isConnected = true
            startListening()
            callback(true)
        } catch (e: IOException) {
            Log.e("BluetoothManager", "Error connecting to device", e)
            disconnect()
            callback(false)
        }
    }

    fun disconnect() {
        try {
            bluetoothSocket?.close()
            outputStream?.close()
            inputStream?.close()
        } catch (e: IOException) {
            Log.e("BluetoothManager", "Error disconnecting", e)
        }
        _isConnected = false
        bluetoothSocket = null
        outputStream = null
        inputStream = null
    }

    fun sendCommand(command: Char) {
        if (!_isConnected) return
        
        try {
            outputStream?.write(command.toInt())
            outputStream?.flush()
        } catch (e: IOException) {
            Log.e("BluetoothManager", "Error sending command", e)
            disconnect()
        }
    }

    fun setDataCallback(callback: (String) -> Unit) {
        dataCallback = callback
    }

    private fun startListening() {
        Thread {
            val buffer = ByteArray(1024)
            var bytes: Int

            while (_isConnected) {
                try {
                    bytes = inputStream?.read(buffer) ?: 0
                    if (bytes > 0) {
                        val data = String(buffer, 0, bytes)
                        handler.post {
                            dataCallback?.invoke(data)
                        }
                    }
                } catch (e: IOException) {
                    Log.e("BluetoothManager", "Error reading data", e)
                    disconnect()
                    break
                }
            }
        }.start()
    }
} 