package com.app.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SearchView searchView;
    private ProgressBar progressBar;
    private List<Channel> channels = new ArrayList<>();
    private ChannelAdapter adapter;

    private final String PLAYLIST = "https://iptv-org.github.io/iptv/index.m3u";

    // ← FIXED: Removed duplicates, keep only unique playlists
    private final String[] ADDITIONAL_PLAYLISTS = {
            "https://iptv-org.github.io/iptv/categories/sports.m3u",
            "https://iptv-org.github.io/iptv/categories/music.m3u",
            "https://iptv-org.github.io/iptv/countries/tz.m3u"

    };

    // ← FIXED: Set to false for faster loading (single playlist)
    private final boolean USE_MULTIPLE_PLAYLISTS = false;  // ← CHANGE to false for speed!

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        searchView = findViewById(R.id.searchView);
        progressBar = findViewById(R.id.progressBar);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Show loading indicator
        progressBar.setVisibility(View.VISIBLE);

        loadChannels();

        // Search filter
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (adapter != null) adapter.filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (adapter != null) adapter.filter(newText);
                return true;
            }
        });
    }

    private void loadChannels() {
        new Thread(() -> {
            long startTime = System.currentTimeMillis();

            // Load from single or multiple playlists
            if (USE_MULTIPLE_PLAYLISTS) {
                channels = loadMultiplePlaylists();
            } else {
                channels = M3UParser.parse(PLAYLIST);
            }

            long loadTime = System.currentTimeMillis() - startTime;

            if (channels == null) channels = new ArrayList<>();

            runOnUiThread(() -> {
                // Hide loading indicator
                progressBar.setVisibility(View.GONE);

                adapter = new ChannelAdapter(channels, channel -> {
                    // Launch PlayerActivity on channel click
                    Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                    intent.putExtra("url", channel.getUrl());
                    startActivity(intent);
                });

                recyclerView.setAdapter(adapter);

                // Optional: Show quick toast with load info (won't interfere)
                Toast.makeText(MainActivity.this,
                        "Loaded " + channels.size() + " channels in " + loadTime + "ms",
                        Toast.LENGTH_SHORT).show();
            });

        }).start();
    }

    // ← FIXED: Added deduplication to avoid duplicate channels
    private List<Channel> loadMultiplePlaylists() {
        Set<String> uniqueUrls = new HashSet<>();  // ← Track unique URLs
        List<Channel> allChannels = new ArrayList<>();
        List<String> playlistUrls = new ArrayList<>();

        // Add main playlist
        playlistUrls.add(PLAYLIST);

        // Add additional playlists (without duplicates)
        for (String url : ADDITIONAL_PLAYLISTS) {
            if (!playlistUrls.contains(url)) {  // ← Avoid duplicates
                playlistUrls.add(url);
            }
        }

        // Load all playlists
        for (int i = 0; i < playlistUrls.size(); i++) {
            String url = playlistUrls.get(i);
            List<Channel> playlistChannels = M3UParser.parse(url);
            if (playlistChannels != null) {
                for (Channel channel : playlistChannels) {
                    // ← Add only unique channels
                    if (!uniqueUrls.contains(channel.getUrl())) {
                        uniqueUrls.add(channel.getUrl());
                        allChannels.add(channel);
                    }
                }
            }
        }

        return allChannels;
    }
}