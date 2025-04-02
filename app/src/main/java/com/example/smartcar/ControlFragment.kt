package com.example.smartcar

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlin.random.Random

class ControlFragment : Fragment() {
    private var isManualMode = true
    private val dateFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    private var consoleText = StringBuilder()
    private val handler = Handler(Looper.getMainLooper())
    private var isMoving = false
    private var currentDirection = "STOP"
    private var rpm = 0
    private var temperature = 25
    private var battery = 100
    private var currentSpeed = 0
    private val maxSpeed = 100
    private val minSpeed = 0
    private val speedIncrement = 10

    private val parameterSimulator = object : Runnable {
        override fun run() {
            if (isMoving) {
                // Simulare RPM bazată pe direcție și viteză
                rpm = when (currentDirection) {
                    "FORWARD", "BACKWARD" -> (800 + (currentSpeed * 4)).coerceAtMost(1200)
                    "LEFT", "RIGHT" -> (400 + (currentSpeed * 2)).coerceAtMost(800)
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_control, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupModeToggle(view)
        setupControlButtons(view)
        setupPedalButtons(view)
        startParameterSimulation()
    }

    private fun setupModeToggle(view: View) {
        val modeToggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.modeToggleGroup)
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

    private fun setupControlButtons(view: View) {
        val forwardButton = view.findViewById<MaterialButton>(R.id.forwardButton)
        val backwardButton = view.findViewById<MaterialButton>(R.id.backwardButton)
        val leftButton = view.findViewById<MaterialButton>(R.id.leftButton)
        val rightButton = view.findViewById<MaterialButton>(R.id.rightButton)

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

    private fun setupPedalButtons(view: View) {
        val accelerateButton = view.findViewById<MaterialButton>(R.id.accelerateButton)
        val brakeButton = view.findViewById<MaterialButton>(R.id.brakeButton)

        accelerateButton.setOnClickListener {
            if (isManualMode) {
                accelerate()
            }
        }

        brakeButton.setOnClickListener {
            if (isManualMode) {
                brake()
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

    private fun accelerate() {
        if (currentSpeed < maxSpeed) {
            currentSpeed = (currentSpeed + speedIncrement).coerceAtMost(maxSpeed)
            logToConsole("Speed increased to: $currentSpeed%")
            updateParameters()
        }
    }

    private fun brake() {
        if (currentSpeed > minSpeed) {
            currentSpeed = (currentSpeed - speedIncrement).coerceAtLeast(minSpeed)
            logToConsole("Speed decreased to: $currentSpeed%")
            updateParameters()
        }
    }

    private fun startParameterSimulation() {
        handler.post(parameterSimulator)
    }

    private fun updateParameters() {
        view?.findViewById<TextView>(R.id.rpmValue)?.text = "RPM: $rpm"
        view?.findViewById<TextView>(R.id.temperatureValue)?.text = "Temperature: ${temperature}°C"
        view?.findViewById<TextView>(R.id.batteryValue)?.text = "Battery: ${battery}%"
        view?.findViewById<TextView>(R.id.speedValue)?.text = "Speed: $currentSpeed%"
    }

    private fun logToConsole(message: String) {
        val timestamp = dateFormat.format(java.util.Date())
        consoleText.append("[$timestamp] $message\n")
        
        // Keep only the last 100 lines
        val lines = consoleText.toString().split("\n")
        if (lines.size > 100) {
            consoleText = StringBuilder(lines.takeLast(100).joinToString("\n"))
        }
        
        view?.findViewById<TextView>(R.id.consoleText)?.text = consoleText.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(parameterSimulator)
    }
} 