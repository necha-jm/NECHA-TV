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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;  // ← ADD THIS IMPORT
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class PlayerActivity extends AppCompatActivity {

    private PlayerView playerView;
    private StreamingService streamingService;
    private boolean isBound = false;
    private FloatingActionButton fabChannels;
    private Button btnPlayPause;
    private Button btnStop;
    private Button btnAspectRatio;  // ← ADD THIS
    private Handler timeoutHandler = new Handler();
    private String currentUrl;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 2;
    private int currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;  // ← ADD THIS

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            StreamingService.LocalBinder binder = (StreamingService.LocalBinder) service;
            streamingService = binder.getService();
            isBound = true;

            if (streamingService.getPlayer() != null) {
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

    // Broadcast receiver for streaming errors
    private BroadcastReceiver errorReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String error = intent.getStringExtra("error");
            String errorType = intent.getStringExtra("errorType");
            handleStreamError(error, errorType);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.playerView);
        fabChannels = findViewById(R.id.fab_channels);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnStop = findViewById(R.id.btn_stop);
        btnAspectRatio = findViewById(R.id.btn_aspect_ratio);  // ← ADD THIS

        currentUrl = getIntent().getStringExtra("url");

        // Check internet before starting
        if (!isInternetAvailable()) {
            showNoInternetDialog();
            return;
        }

        // Register error receiver
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(errorReceiver, new IntentFilter("STREAMING_ERROR"));

        startStreamingService(currentUrl);

        Intent serviceIntent = new Intent(this, StreamingService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

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

        btnStop.setOnClickListener(v -> {
            if (streamingService != null) {
                streamingService.stopPlayback();
            }
            finish();
        });

        fabChannels.setOnClickListener(v -> {
            Intent intent = new Intent(PlayerActivity.this, MainActivity.class);
            startActivity(intent);
        });

        // ← ADD THIS: Aspect Ratio Button Click Listener
        btnAspectRatio.setOnClickListener(v -> {
            toggleAspectRatio();
        });

        // Timeout after 10 seconds
        timeoutHandler.postDelayed(() -> {
            if (!isBound || streamingService == null || !streamingService.isPlaying()) {
                showConnectionTimeoutDialog();
            }
        }, 10000);
    }

    // ← ADD THIS METHOD: Toggle between different aspect ratios
    private void toggleAspectRatio() {
        switch (currentResizeMode) {
            case AspectRatioFrameLayout.RESIZE_MODE_FIT:
                currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                btnAspectRatio.setText("Fill");
                btnAspectRatio.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.aspect_ratio_fill)));
                Toast.makeText(this, "Fill Mode - Stretches to fill screen", Toast.LENGTH_SHORT).show();
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_FILL:
                currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                btnAspectRatio.setText("Zoom");
                btnAspectRatio.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.aspect_ratio_zoom)));
                Toast.makeText(this, "Zoom Mode - Full screen (may crop edges)", Toast.LENGTH_SHORT).show();
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_ZOOM:
                currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                btnAspectRatio.setText("Fit");
                btnAspectRatio.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.aspect_ratio_fit)));
                Toast.makeText(this, "Fit Mode - Full video (may have black bars)", Toast.LENGTH_SHORT).show();
                break;
        }

        if (playerView != null) {
            playerView.setResizeMode(currentResizeMode);
        }
    }

    // ... rest of your existing code (handleStreamError, showVPNDialog, etc.) ...

    private void handleStreamError(String error, String errorType) {
        if ("GEO_BLOCKED".equals(errorType)) {
            showVPNDialog(error);
        } else if ("NETWORK_ERROR".equals(errorType)) {
            showNetworkErrorDialog(error);
        } else if ("TIMEOUT".equals(errorType)) {
            showTimeoutDialog();
        } else {
            showGenericErrorDialog(error);
        }
    }

    private void showVPNDialog(String errorMessage) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🌍 Channel Not Available in Your Region");
        builder.setMessage(errorMessage + "\n\nThis channel is geo-blocked and cannot be accessed from your current location.\n\n💡 Suggestion: Use a VPN to change your virtual location to a country where this channel is available.\n\nRecommended VPNs:\n• ProtonVPN (Free)\n• Windscribe (Free)\n• ExpressVPN (Paid)\n• NordVPN (Paid)");

        builder.setPositiveButton("Try VPN", (dialog, which) -> {
            openVPNSuggestion();
        });

        builder.setNegativeButton("Back to Channels", (dialog, which) -> {
            finish();
        });

        builder.setNeutralButton("Retry", (dialog, which) -> {
            if (retryCount < MAX_RETRIES) {
                retryCount++;
                Toast.makeText(this, "Retrying... (" + retryCount + "/" + MAX_RETRIES + ")", Toast.LENGTH_SHORT).show();
                startStreamingService(currentUrl);
            } else {
                Toast.makeText(this, "Max retries reached. Please try another channel.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void openVPNSuggestion() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔒 VPN Options");
        builder.setMessage("Choose a VPN to bypass geo-restrictions:\n\n" +
                "1. 🔓 Free VPNs:\n" +
                "   • ProtonVPN - Unlimited data, no logs\n" +
                "   • Windscribe - 10GB free data\n\n" +
                "2. 💰 Premium VPNs:\n" +
                "   • ExpressVPN - Fastest speeds\n" +
                "   • NordVPN - Best security\n\n" +
                "After installing a VPN:\n" +
                "1. Connect to a server in a country where the channel works\n" +
                "2. Return to this app and try again");

        builder.setPositiveButton("Open Play Store", (dialog, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(android.net.Uri.parse("market://details?id=com.protonvpn.android"));
            startActivity(intent);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            finish();
        });

        builder.show();
    }

    private void showNoInternetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📡 No Internet Connection");
        builder.setMessage("Please check your internet connection and try again.");
        builder.setPositiveButton("Retry", (dialog, which) -> {
            if (isInternetAvailable()) {
                recreate();
            } else {
                finish();
            }
        });
        builder.setNegativeButton("Exit", (dialog, which) -> finish());
        builder.setCancelable(false);
        builder.show();
    }

    private void showNetworkErrorDialog(String error) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚠️ Network Error");
        builder.setMessage(error + "\n\nPlease check your internet connection.");
        builder.setPositiveButton("Retry", (dialog, which) -> {
            startStreamingService(currentUrl);
        });
        builder.setNegativeButton("Back", (dialog, which) -> finish());
        builder.show();
    }

    private void showConnectionTimeoutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⏱️ Connection Timeout");
        builder.setMessage("The server is taking too long to respond.\n\nThis could be due to:\n• Slow internet connection\n• Server is down\n• Channel is temporarily unavailable");

        builder.setPositiveButton("Retry", (dialog, which) -> {
            startStreamingService(currentUrl);
        });

        builder.setNegativeButton("Back to Channels", (dialog, which) -> {
            finish();
        });

        builder.show();
    }

    private void showTimeoutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⏱️ Connection Timeout");
        builder.setMessage("The server is not responding.\n\n💡 Tip: Try a different channel or check your internet connection.");
        builder.setPositiveButton("Try Another Channel", (dialog, which) -> finish());
        builder.setNegativeButton("Retry", (dialog, which) -> {
            startStreamingService(currentUrl);
        });
        builder.show();
    }

    private void showGenericErrorDialog(String error) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("❌ Playback Error");
        builder.setMessage(error + "\n\nPlease try another channel.");
        builder.setPositiveButton("OK", (dialog, which) -> finish());
        builder.show();
    }

    private void startStreamingService(String url) {
        Intent intent = new Intent(this, StreamingService.class);
        intent.putExtra("url", url);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void updateUI() {
        if (streamingService != null && btnPlayPause != null) {
            btnPlayPause.setText(streamingService.isPlaying() ? "Pause" : "Play");
        }
    }

    public boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeoutHandler.removeCallbacksAndMessages(null);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(errorReceiver);
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }
}