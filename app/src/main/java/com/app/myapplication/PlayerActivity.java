package com.app.myapplication;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.app.myapplication.MainActivity;
import com.app.myapplication.R;
import com.app.myapplication.StreamingService;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class PlayerActivity extends AppCompatActivity {

    private PlayerView playerView;
    private StreamingService streamingService;
    private boolean isBound = false;

    private FloatingActionButton fabChannels;
    private Button btnPlayPause, btnStop, btnAspectRatio;

    private Handler timeoutHandler = new Handler();

    private String currentUrl;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 2;

    private int currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;

    // ✅ SERVICE CONNECTION (SAFE)
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            StreamingService.LocalBinder binder = (StreamingService.LocalBinder) service;
            streamingService = binder.getService();
            isBound = true;

            if (streamingService != null && streamingService.getPlayer() != null) {
                playerView.setPlayer(streamingService.getPlayer());
            }

            updateUI();
            timeoutHandler.removeCallbacksAndMessages(null);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            streamingService = null;
        }
    };

    // ✅ ERROR RECEIVER (SAFE)
    private final BroadcastReceiver errorReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String error = intent.getStringExtra("error");
            String type = intent.getStringExtra("errorType");
            handleStreamError(error, type);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // 🔹 INIT UI
        playerView = findViewById(R.id.playerView);
        fabChannels = findViewById(R.id.fab_channels);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnStop = findViewById(R.id.btn_stop);
        btnAspectRatio = findViewById(R.id.btn_aspect_ratio);

        // 🔹 GET URL SAFELY
        currentUrl = getIntent().getStringExtra("url");

        if (currentUrl == null || currentUrl.isEmpty()) {
            Toast.makeText(this, "Invalid channel URL", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 🔹 CHECK INTERNET
        if (!isInternetAvailable()) {
            showNoInternetDialog();
            return;
        }

        // 🔹 REGISTER RECEIVER SAFELY
        try {
            LocalBroadcastManager.getInstance(this)
                    .registerReceiver(errorReceiver, new IntentFilter("STREAMING_ERROR"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 🔹 START SERVICE
        startStreamingService(currentUrl);

        Intent intent = new Intent(this, StreamingService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        // 🔹 BUTTON ACTIONS
        if (btnPlayPause != null) {
            btnPlayPause.setOnClickListener(v -> {
                if (streamingService != null) {
                    if (streamingService.isPlaying()) {
                        streamingService.pausePlayback();
                    } else {
                        streamingService.resumePlayback();
                    }
                    updateUI();
                }
            });
        }

        if (btnStop != null) {
            btnStop.setOnClickListener(v -> {
                if (streamingService != null) {
                    streamingService.stopPlayback();
                }
                finish();
            });
        }

        if (fabChannels != null) {
            fabChannels.setOnClickListener(v -> {
                startActivity(new Intent(this, MainActivity.class));
            });
        }

        if (btnAspectRatio != null) {
            btnAspectRatio.setOnClickListener(v -> toggleAspectRatio());
        }

        // 🔹 TIMEOUT (10s)
        timeoutHandler.postDelayed(() -> {
            if (!isBound || streamingService == null || !streamingService.isPlaying()) {
                showConnectionTimeoutDialog();
            }
        }, 10000);
    }

    // ✅ ASPECT RATIO (FIXED FOR ALL DEVICES)
    private void toggleAspectRatio() {
        if (btnAspectRatio == null) return;

        switch (currentResizeMode) {

            case AspectRatioFrameLayout.RESIZE_MODE_FIT:
                currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                btnAspectRatio.setText("Fill");
                btnAspectRatio.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.aspect_ratio_fill)));
                break;

            case AspectRatioFrameLayout.RESIZE_MODE_FILL:
                currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                btnAspectRatio.setText("Zoom");
                btnAspectRatio.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.aspect_ratio_zoom)));
                break;

            default:
                currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                btnAspectRatio.setText("Fit");
                btnAspectRatio.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.aspect_ratio_fit)));
                break;
        }

        if (playerView != null) {
            playerView.setResizeMode(currentResizeMode);
        }
    }

    // ✅ ERROR HANDLING
    private void handleStreamError(String error, String type) {
        if ("GEO_BLOCKED".equals(type)) {
            showMessage("Channel blocked in your region");
        } else if ("NETWORK_ERROR".equals(type)) {
            showMessage("Network error");
        } else {
            showMessage("Playback error");
        }
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    // ✅ INTERNET CHECK (ALL DEVICES)
    public boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } else {
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        }
    }

    // ✅ START SERVICE SAFE
    private void startStreamingService(String url) {
        Intent intent = new Intent(this, StreamingService.class);
        intent.putExtra("url", url);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Cannot start streaming", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUI() {
        if (streamingService != null && btnPlayPause != null) {
            btnPlayPause.setText(streamingService.isPlaying() ? "Pause" : "Play");
        }
    }

    private void showNoInternetDialog() {
        new AlertDialog.Builder(this)
                .setTitle("No Internet")
                .setMessage("Check your connection")
                .setPositiveButton("Retry", (d, w) -> recreate())
                .setNegativeButton("Exit", (d, w) -> finish())
                .show();
    }

    private void showConnectionTimeoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Timeout")
                .setMessage("Channel not responding")
                .setPositiveButton("Retry", (d, w) -> startStreamingService(currentUrl))
                .setNegativeButton("Back", (d, w) -> finish())
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        timeoutHandler.removeCallbacksAndMessages(null);

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(errorReceiver);
        } catch (Exception ignored) {}

        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }

        // Check if this is the final activity being destroyed
        if (isFinishing()) {
            // User explicitly closed the app
            if (streamingService != null) {
                streamingService.stopPlayback();
            }
            Intent stopIntent = new Intent(this, StreamingService.class);
            stopService(stopIntent);
        }
    }
}