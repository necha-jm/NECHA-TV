package com.app.myapplication;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class PlayerActivity extends AppCompatActivity {

    private PlayerView playerView;
    private StreamingService streamingService;
    private boolean isBound = false;
    private FloatingActionButton fabChannels;
    private Button btnPlayPause;
    private Button btnStop;
    private Handler timeoutHandler = new Handler();  // ← ADD THIS LINE

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

            // ← ADD THIS BLOCK (Cancel timeout on success)
            timeoutHandler.removeCallbacksAndMessages(null);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            streamingService = null;
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

        String videoUrl = getIntent().getStringExtra("url");

        // ← ADD THIS LINE (Check internet before starting)
        if (!isInternetAvailable()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        startStreamingService(videoUrl);

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

        // ← ADD THIS BLOCK (Timeout after 8 seconds)
        timeoutHandler.postDelayed(() -> {
            if (!isBound || streamingService == null || !streamingService.isPlaying()) {
                Toast.makeText(PlayerActivity.this, "Connection timeout", Toast.LENGTH_SHORT).show();
                finish();
            }
        }, 8000);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeoutHandler.removeCallbacksAndMessages(null);  // ← ADD THIS LINE
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    public boolean isInternetAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) return false;

        NetworkCapabilities nc =
                cm.getNetworkCapabilities(cm.getActiveNetwork());

        return nc != null &&
                nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}