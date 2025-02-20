package com.example.pingpong_gen1;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private int sampleRate = 10;  // default to 10Hz
    private ScheduledExecutorService scheduler;
    private String apIpAddress;   // To store the AP IP address
    private TextView pingStatusTextView;  // To display ping results
    private TextView gyroStatusTextView;  // To display phone orientation
    private int pingCount = 0;  // Counter to keep track of pings
    private long startTime;  // To track the start time for frequency calculation

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private float[] gravity = new float[3];
    private float[] geomagnetic = new float[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        Spinner rateSpinner = findViewById(R.id.rateSpinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.sample_rates, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        rateSpinner.setAdapter(adapter);

        // Handle spinner selection
        rateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedRate = parent.getItemAtPosition(position).toString();
                sampleRate = Integer.parseInt(selectedRate);  // Set sample rate from selection
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        EditText apIpEditText = findViewById(R.id.apIpEditText);
        pingStatusTextView = findViewById(R.id.pingStatusTextView);
        gyroStatusTextView = findViewById(R.id.gyroStatusTextView);  // TextView for orientation

        // Initialize SensorManager and sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        // Register accelerometer and magnetometer listeners
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);

        // Start button click handler
        Button startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                apIpAddress = apIpEditText.getText().toString();
                if (!apIpAddress.isEmpty()) {
                    startPinging(sampleRate);
                } else {
                    Log.e("Ping", "IP Address is empty");
                    pingStatusTextView.setText("Please enter a valid IP address.");
                }
            }
        });

        // Stop button click handler
        Button stopButton = findViewById(R.id.stopButton);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pingCount = 0;
                stopPinging();
            }
        });
    }

    // Sensor data listener (for accelerometer and magnetometer)
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values;
        }

        if (gravity != null && geomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];

            boolean success = SensorManager.getRotationMatrix(R, I, gravity, geomagnetic);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);

                // orientation[0]: Azimuth (Yaw)   [-π, π]
                // orientation[1]: Pitch          [-π/2, π/2]
                // orientation[2]: Roll           [-π, π]

                float azimuth = (float) Math.toDegrees(orientation[0]);  // Yaw
                float pitch = (float) Math.toDegrees(orientation[1]);    // Pitch
                float roll = (float) Math.toDegrees(orientation[2]);     // Roll

                // Ensure azimuth is in [0,360)
                if (azimuth < 0) azimuth += 360;

                // Update UI with orientation data
                String orientationText = String.format("Yaw: %.2f°\nPitch: %.2f°\nRoll: %.2f°",
                        azimuth, pitch, roll);
                runOnUiThread(() -> gyroStatusTextView.setText(orientationText));
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    // Method to start pinging
    private void startPinging(final double rate) {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }

        long intervalMillis = (long) (1000 / rate);
        pingCount = 0;
        startTime = System.nanoTime();

        scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            sendPing();

            long currentTime = System.nanoTime();
            long elapsedTime = currentTime - startTime;
            double actualFrequency = (pingCount / (elapsedTime / 1_000_000_000.0));

            runOnUiThread(() -> {
                String frequencyInfo = "Packets Sent: " + pingCount +
                        "\nActual Frequency: " + String.format("%.2f", actualFrequency) + " Hz";
                pingStatusTextView.setText(frequencyInfo);
            });
        }, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    // Method to stop pinging
    private void stopPinging() {
        if (scheduler != null) {
            scheduler.shutdown();
            Log.d("Ping", "Pinging stopped.");
        }
    }

    // Method to send a ping command
    private void sendPing() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            String pingCommand = "/system/bin/ping -c 1 -W 200 -s 56 " + apIpAddress;
            try {
                Process process = Runtime.getRuntime().exec(pingCommand);
                int exitValue = process.waitFor();

                if (exitValue == 0) {
                    Log.d("Ping", "Ping successful to " + apIpAddress);
                    pingCount++;
                } else {
                    Log.d("Ping", "Ping failed to " + apIpAddress);
                }

            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                Log.e("Ping", "Error during ping: " + e.getMessage());
            }
        });
        executorService.shutdown();
    }
}
