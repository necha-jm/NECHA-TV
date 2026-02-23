package com.app.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class PlayerActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayer player;
    private FloatingActionButton fabChannels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.playerView);
        fabChannels = findViewById(R.id.fab_channels);

        // Default stream if none provided
        String videoUrl = getIntent().getStringExtra("url");
        if (videoUrl == null || videoUrl.isEmpty()) {
            videoUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";
        }

        // Initialize player
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(videoUrl)
                .setMimeType("application/x-mpegURL")
                .build();
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();




        // FAB click opens MainActivity (channel list)
        fabChannels.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(PlayerActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}