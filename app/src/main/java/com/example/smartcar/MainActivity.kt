package com.example.smartcar

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private var isManualMode = true
    private val dateFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    private var consoleText = StringBuilder()
    private val handler = Handler(Looper.getMainLooper())
    private var isMoving = false
    private var currentDirection = "STOP"
    private var rpm = 0
    private var temperature = 25
    private var battery = 100

    // Runnable pentru simularea parametrilor
    private val parameterSimulator = object : Runnable {
        override fun run() {
            if (isMoving) {
                // Simulare RPM bazată pe direcție
                rpm = when (currentDirection) {
                    "FORWARD", "BACKWARD" -> Random.nextInt(800, 1200)
                    "LEFT", "RIGHT" -> Random.nextInt(400, 800)
                    else -> 0
                }

                // Simulare temperatură
                temperature += Random.nextInt(-1, 2)
                temperature = temperature.coerceIn(20, 40)

                // Simulare baterie
                if (Random.nextFloat() < 0.1f) {
                    battery = (battery - 1).coerceAtLeast(0)
                }
            } else {
                rpm = 0
                temperature = (temperature - 0.5f).toInt().coerceAtLeast(20)
            }

            updateParameters()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupModeToggle()
        setupControlButtons()
        startParameterSimulation()
    }

    private fun setupModeToggle() {
        val modeToggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.modeToggleGroup)
        modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isManualMode = checkedId == R.id.manualModeButton
                logToConsole("Mode changed to: ${if (isManualMode) "Manual" else "Automatic"}")
                if (!isManualMode) {
                    startAutomaticMode()
                } else {
                    stopMovement()
                }
            }
        }
    }

    private fun setupControlButtons() {
        val forwardButton = findViewById<MaterialButton>(R.id.forwardButton)
        val backwardButton = findViewById<MaterialButton>(R.id.backwardButton)
        val leftButton = findViewById<MaterialButton>(R.id.leftButton)
        val rightButton = findViewById<MaterialButton>(R.id.rightButton)

        // Set button rotations
        forwardButton.rotation = 270f
        backwardButton.rotation = 90f
        leftButton.rotation = 180f

        forwardButton.setOnClickListener {
            if (isManualMode) {
                moveCar("FORWARD")
            }
        }

        backwardButton.setOnClickListener {
            if (isManualMode) {
                moveCar("BACKWARD")
            }
        }

        leftButton.setOnClickListener {
            if (isManualMode) {
                moveCar("LEFT")
            }
        }

        rightButton.setOnClickListener {
            if (isManualMode) {
                moveCar("RIGHT")
            }
        }
    }

    private fun moveCar(direction: String) {
        currentDirection = direction
        isMoving = true
        logToConsole("Moving $direction")
    }

    private fun stopMovement() {
        isMoving = false
        currentDirection = "STOP"
        logToConsole("Stopped")
    }

    private fun startAutomaticMode() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!isManualMode) {
                    val directions = listOf("FORWARD", "LEFT", "RIGHT", "BACKWARD")
                    val randomDirection = directions.random()
                    moveCar(randomDirection)
                    handler.postDelayed(this, Random.nextLong(2000, 5000))
                }
            }
        }, 1000)
    }

    private fun startParameterSimulation() {
        handler.post(parameterSimulator)
    }

    private fun updateParameters() {
        findViewById<TextView>(R.id.rpmValue).text = "RPM: $rpm"
        findViewById<TextView>(R.id.temperatureValue).text = "Temperature: ${temperature}°C"
        findViewById<TextView>(R.id.batteryValue).text = "Battery: ${battery}%"
    }

    private fun logToConsole(message: String) {
        val timestamp = dateFormat.format(java.util.Date())
        consoleText.append("[$timestamp] $message\n")
        
        // Keep only the last 100 lines
        val lines = consoleText.toString().split("\n")
        if (lines.size > 100) {
            consoleText = StringBuilder(lines.takeLast(100).joinToString("\n"))
        }
        
        findViewById<TextView>(R.id.consoleText).text = consoleText.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(parameterSimulator)
    }
} 