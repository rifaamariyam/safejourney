package com.safejourney.app

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.*
import android.telephony.SmsManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.*
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var txtRisk: TextView
    private lateinit var txtETA: TextView
    private lateinit var txtCountdown: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentLat = 0.0
    private var currentLon = 0.0
    private var destinationLat = 0.0
    private var destinationLon = 0.0

    private var calculatedDurationMillis: Long = 0
    private var countdownTimer: CountDownTimer? = null
    private var routeLine: Polyline? = null

    private val deviationThreshold = 100.0 // meters
    private var taxiModeEnabled = false
    private var deviationTriggered = false

    // Demo risk dataset
    data class AreaRiskData(
        val lat: Double,
        val lon: Double,
        val streetLights: Int,
        val residentialDensity: Int,
        val crimeIndex: Int
    )

    private val demoZones = listOf(
        AreaRiskData(9.9312, 76.2673, 20, 80, 65),
        AreaRiskData(9.9000, 76.2500, 8, 40, 40),
        AreaRiskData(9.8500, 76.2000, 3, 15, 20)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtRisk = findViewById(R.id.txtRisk)
        txtETA = findViewById(R.id.txtETA)
        txtCountdown = findViewById(R.id.txtCountdown)

        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(18.0)

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        requestPermissions()
        startLiveLocation()
        setupSpinner()

        findViewById<Button>(R.id.btnStartJourney)
            .setOnClickListener {
                if (calculatedDurationMillis > 0)
                    startCountdown(calculatedDurationMillis)
            }

        findViewById<Button>(R.id.btnStopJourney)
            .setOnClickListener {

                countdownTimer?.cancel()
                txtCountdown.text = "Stopped"

                // Stop taxi monitoring
                taxiModeEnabled = false
                deviationTriggered = false

                // Remove route line
                routeLine?.let { map.overlays.remove(it) }
                routeLine = null
                map.invalidate()
            }

        findViewById<Button>(R.id.btnSOS)
            .setOnClickListener { sendSOS() }
    }

    // ---------------- LOCATION ----------------

    private fun startLiveLocation() {

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 3000
        ).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {

                val location = result.lastLocation ?: return
                currentLat = location.latitude
                currentLon = location.longitude

                val point = GeoPoint(currentLat, currentLon)

                map.overlays.clear()

                val marker = Marker(map)
                marker.position = point
                marker.setAnchor(
                    Marker.ANCHOR_CENTER,
                    Marker.ANCHOR_BOTTOM
                )
                map.overlays.add(marker)

                routeLine?.let { map.overlays.add(it) }

                if (taxiModeEnabled && !deviationTriggered) {
                    checkDeviation()
                }

                map.controller.setCenter(point)
                map.invalidate()
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                request, callback, mainLooper
            )
        }
    }

    // ---------------- SPINNER ----------------

    private fun setupSpinner() {

        val spinner = findViewById<Spinner>(R.id.spinnerMode)

        val modes = arrayOf(
            "Select Mode",
            "Walking",
            "Bike",
            "Car",
            "Taxi"
        )

        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            modes
        )

        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    if (position != 0)
                        calculateETAAndRisk()
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) {}
            }
    }

    // ---------------- ETA + ROUTE ----------------

    private fun calculateETAAndRisk() {

        val destination =
            findViewById<EditText>(R.id.edtDestination)
                .text.toString()

        if (destination.isEmpty()) return

        val geocoder = Geocoder(this, Locale.getDefault())
        val address =
            geocoder.getFromLocationName(destination, 1)
                ?: return

        if (address.isEmpty()) return

        destinationLat = address[0].latitude
        destinationLon = address[0].longitude

        val mode =
            findViewById<Spinner>(R.id.spinnerMode)
                .selectedItem.toString()

        val distanceKm =
            haversine(currentLat, currentLon,
                destinationLat, destinationLon)

        val speed = when(mode) {
            "Walking" -> 5
            "Bike" -> 15
            "Car" -> 40
            "Taxi" -> 40
            else -> return
        }

        taxiModeEnabled = (mode == "Taxi")
        deviationTriggered = false

        val minutes =
            ((distanceKm / speed) * 60).toInt()

        calculatedDurationMillis =
            (minutes * 60 * 1000).toLong()

        txtETA.text = "Estimated Time: $minutes mins"

        if (taxiModeEnabled)
            drawRoute()

        calculateRisk(destinationLat, destinationLon)
    }

    private fun drawRoute() {

        routeLine = Polyline()
        routeLine!!.setPoints(
            listOf(
                GeoPoint(currentLat, currentLon),
                GeoPoint(destinationLat, destinationLon)
            )
        )

        routeLine!!.outlinePaint.color =
            android.graphics.Color.BLUE
        routeLine!!.outlinePaint.strokeWidth = 8f
    }

    // ---------------- DEVIATION ----------------

    private fun checkDeviation() {

        val distance = distanceFromLine(
            currentLat,
            currentLon,
            destinationLat,
            destinationLon
        )

        if (distance > deviationThreshold) {

            deviationTriggered = true
            taxiModeEnabled = false

            Toast.makeText(
                this,
                "⚠ Route Deviation Detected!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun distanceFromLine(
        userLat: Double,
        userLon: Double,
        destLat: Double,
        destLon: Double
    ): Double {

        return haversine(
            userLat,
            userLon,
            destLat,
            destLon
        ) * 1000
    }

    // ---------------- RISK ----------------

    private fun calculateRisk(
        destLat: Double,
        destLon: Double
    ) {

        var nearest = demoZones[0]
        var minDistance = Double.MAX_VALUE

        for (zone in demoZones) {
            val d = haversine(
                destLat, destLon,
                zone.lat, zone.lon
            )
            if (d < minDistance) {
                minDistance = d
                nearest = zone
            }
        }

        val risk =
            (nearest.crimeIndex * 0.5 +
                    nearest.residentialDensity * 0.3 +
                    (if (nearest.streetLights < 5) 20 else 5)
                    ).toInt()

        txtRisk.text =
            "Destination Risk: $risk%"

        when {
            risk >= 70 ->
                txtRisk.setTextColor(android.graphics.Color.RED)
            risk >= 40 ->
                txtRisk.setTextColor(android.graphics.Color.parseColor("#FF9800"))
            else ->
                txtRisk.setTextColor(android.graphics.Color.GREEN)
        }
    }

    // ---------------- COUNTDOWN ----------------

    private fun startCountdown(duration: Long) {

        countdownTimer?.cancel()

        countdownTimer =
            object : CountDownTimer(duration, 1000) {

                override fun onTick(ms: Long) {
                    val minutes =
                        (ms / 1000) / 60
                    val seconds =
                        (ms / 1000) % 60

                    txtCountdown.text =
                        "Countdown: ${minutes}m ${seconds}s"
                }

                override fun onFinish() {
                    sendSOS()
                }
            }.start()
    }

    // ---------------- UTIL ----------------

    private fun haversine(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {

        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat/2).pow(2.0) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLon/2).pow(2.0)

        val c = 2 * atan2(sqrt(a), sqrt(1-a))

        return R * c
    }

    private fun sendSOS() {

        val sms = SmsManager.getDefault()
        sms.sendTextMessage(
            "1234567890",
            null,
            "🚨 SOS! Location: https://maps.google.com/?q=$currentLat,$currentLon",
            null,
            null
        )

        Toast.makeText(
            this,
            "SOS Sent",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun requestPermissions() {

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.SEND_SMS,
                Manifest.permission.INTERNET
            ),
            100
        )
    }
}