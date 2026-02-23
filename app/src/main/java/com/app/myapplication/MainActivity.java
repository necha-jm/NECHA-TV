package com.app.myapplication;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private RecyclerView recyclerView;
    private ExoPlayer player;

    private List<Channel> channels = new ArrayList<>();

    private final String PLAYLIST =
            "https://iptv-org.github.io/iptv/index.m3u";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.playerView);
        recyclerView = findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(
                new LinearLayoutManager(this)
        );

        initPlayer();
        loadChannels();
    }

    // Initialize player once
    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
    }

    private void loadChannels() {

        new Thread(() -> {

            channels = M3UParser.parse(PLAYLIST);

            runOnUiThread(() -> {

                ChannelAdapter adapter =
                        new ChannelAdapter(channels, channel -> {
                            playChannel(channel.getUrl());
                        });

                recyclerView.setAdapter(adapter);

                // Auto play first channel
                if (channels != null && !channels.isEmpty()) {
                    playChannel(channels.get(0).getUrl());
                }

            });

        }).start();
    }

    private void playChannel(String url) {

        if (player == null) return;

        MediaItem mediaItem = MediaItem.fromUri(url);

        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
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