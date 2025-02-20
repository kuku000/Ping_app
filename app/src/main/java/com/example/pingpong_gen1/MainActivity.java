package com.example.pingpong_gen1;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {

    private int sampleRate = 10;  // default to 10Hz
    private ScheduledExecutorService scheduler;
    private String apIpAddress;   // To store the AP IP address
    private TextView pingStatusTextView;  // To display ping results
    private int pingCount = 0;  // Counter to keep track of pings
    private long startTime;  // To track the start time for frequency calculation

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        // Retrieve IP address from the input field
        EditText apIpEditText = findViewById(R.id.apIpEditText);
        pingStatusTextView = findViewById(R.id.pingStatusTextView);  // Get reference to the TextView

        // Start button click handler
        Button startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                apIpAddress = apIpEditText.getText().toString();  // Get the IP address from user input
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
                stopPinging();  // Call the method to stop pinging
            }
        });
    }

    // Method to start pinging at the selected rate
    private void startPinging(final double rate) {
        // 停止之前的排程
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();  // 停止排程器
        }

        // 計算間隔時間（毫秒）
        long intervalMillis = (long) (1000 / rate);
        pingCount = 0;  // 初始化計數器
        startTime = System.nanoTime();  // 使用 nanoTime 來計算精確的時間

        // 啟動新的排程
        scheduler = Executors.newSingleThreadScheduledExecutor();

        // 使用 fixedRate 保證間隔時間固定
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                sendPing();  // 執行 ping

                // 計算頻率，不更新 UI
                long currentTime = System.nanoTime();
                long elapsedTime = currentTime - startTime;
                double actualFrequency = (pingCount / (elapsedTime / 1000000000.0));  // 轉換為秒來計算頻率

                // 每發送 100 封包後重設計數器和起始時間
//                if (pingCount >= 100) {
//                    pingCount = 0;  // 重設計數器
//                    startTime = System.nanoTime();  // 重設起始時間
//                }

                // 顯示發送的封包數
                runOnUiThread(() -> {
                    String frequencyInfo = "Packets Sent: " + pingCount +
                            "\nActual Frequency: " + String.format("%.2f", actualFrequency) + " Hz";
                    pingStatusTextView.setText(frequencyInfo);  // 更新 UI 顯示
                });
            }
        }, 0, intervalMillis, TimeUnit.MILLISECONDS);  // 根據 rate 設置發送間隔
    }

    // Method to stop pinging
    private void stopPinging() {
        if (scheduler != null) {
            scheduler.shutdown();  // Stop the scheduler
            Log.d("Ping", "Pinging stopped.");
        }
    }

    // Method to send a ping command
    private void sendPing() {
        // 使用 ExecutorService 來確保 ping 在子線程中執行
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            String pingCommand = "/system/bin/ping -c 1 -W 200 -s 56  " + apIpAddress;  // 使用更短的等待時間
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
