package com.example.neurallinkporter

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class ThermalService : Service() {

    private val TAG = "ThermalService"
    private val pollIntervalMs = 500L // check twice per second
    private lateinit var handler: Handler
    private var workerJob: Job? = null

    // ==== CONFIGURATION ==== //
    private val serverIp = "192.168.1.100" // TODO: replace with your PC LAN IP
    private val serverPort = 9999
    // ====================== //

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread("ThermalThread")
        thread.start()
        handler = Handler(thread.looper)

        workerJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val telemetry = collectTelemetry()
                if (shouldOffload(telemetry)) {
                    offloadHeavyTask(telemetry)
                }
                Thread.sleep(pollIntervalMs)
            }
        }
        Log.i(TAG, "ThermalService started")
    }

    // -------------------------------------------------
    // Data class representing a snapshot of device temperature
    // -------------------------------------------------
    private data class Telemetry(
        val cpuTempC: Float,
        val gpuTempC: Float,
        val throttling: Boolean
    )

    /**
     * Gather temperature information.
     *
     * - `BatteryManager` provides a generic battery temperature which we use as a fallback.
     * - On Android 13+ we could read per‑zone data via `ThermalManager` (omitted for brevity).
     */
    private fun collectTelemetry(): Telemetry {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        // Battery temperature reported in tenths of a degree Celsius
        val batteryTemp = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_TEMPERATURE) / 10f

        // Simple throttling heuristic: if battery temperature exceeds 45 °C we consider the device throttling
        val throttling = batteryTemp > 45f

        // On many devices the GPU and CPU share the same thermal sensor; we reuse the battery temp.
        return Telemetry(
            cpuTempC = batteryTemp,
            gpuTempC = batteryTemp,
            throttling = throttling
        )
    }

    private fun shouldOffload(t: Telemetry): Boolean {
        return t.gpuTempC > 80f || t.throttling
    }

    /**
     * Serialize a minimal JSON payload and send it via UDP to the PC server.
     */
    private fun offloadHeavyTask(t: Telemetry) {
        val payload = """
        {
            \"task_id\": \"${System.currentTimeMillis()}\",
            \"type\": \"pathfinding\",
            \"payload\": {
                \"cpu_temp\": ${t.cpuTempC},
                \"gpu_temp\": ${t.gpuTempC}
            }
        }
        """.trimIndent()
        try {
            val socket = DatagramSocket()
            val address = InetAddress.getByName(serverIp)
            val data = payload.toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(data, data.size, address, serverPort)
            socket.send(packet)
            socket.close()
            Log.d(TAG, "Offloaded task, ${data.size} bytes to $serverIp:$serverPort")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send UDP packet", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        workerJob?.cancel()
        handler.looper.quit()
        Log.i(TAG, "ThermalService stopped")
    }
}
