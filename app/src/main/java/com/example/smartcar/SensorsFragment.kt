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
import kotlin.random.Random

class SensorsFragment : Fragment() {
    private val handler = Handler(Looper.getMainLooper())
    private var isSimulating = false

    private val sensorSimulator = object : Runnable {
        override fun run() {
            if (isSimulating) {
                // Simulate gas sensor (0-1000 PPM)
                val gasValue = Random.nextInt(0, 1000)
                view?.findViewById<TextView>(R.id.gasValue)?.text = "Gas Level: $gasValue PPM"

                // Simulate ultrasonic sensor (0-400 cm)
                val ultrasonicValue = Random.nextInt(0, 400)
                view?.findViewById<TextView>(R.id.ultrasonicValue)?.text = "Distance: $ultrasonicValue cm"

                // Simulate audio sensor (30-120 dB)
                val audioValue = Random.nextInt(30, 120)
                view?.findViewById<TextView>(R.id.audioValue)?.text = "Sound Level: $audioValue dB"

                // Simulate rain sensor (0-100%)
                val rainValue = Random.nextInt(0, 100)
                view?.findViewById<TextView>(R.id.rainValue)?.text = "Rain Level: $rainValue%"

                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sensors, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup button click listeners
        view.findViewById<MaterialButton>(R.id.addGasSensorButton).setOnClickListener {
            startSensorSimulation()
        }

        view.findViewById<MaterialButton>(R.id.addUltrasonicSensorButton).setOnClickListener {
            startSensorSimulation()
        }

        view.findViewById<MaterialButton>(R.id.addAudioSensorButton).setOnClickListener {
            startSensorSimulation()
        }

        view.findViewById<MaterialButton>(R.id.addRainSensorButton).setOnClickListener {
            startSensorSimulation()
        }
    }

    private fun startSensorSimulation() {
        if (!isSimulating) {
            isSimulating = true
            handler.post(sensorSimulator)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isSimulating = false
        handler.removeCallbacks(sensorSimulator)
    }
} 