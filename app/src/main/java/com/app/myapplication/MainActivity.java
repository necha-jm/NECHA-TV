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
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SearchView searchView;
    private ProgressBar progressBar;  // ← ADD THIS
    private List<Channel> channels = new ArrayList<>();
    private ChannelAdapter adapter;

    private final String PLAYLIST = "https://iptv-org.github.io/iptv/index.m3u";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        searchView = findViewById(R.id.searchView);
        progressBar = findViewById(R.id.progressBar);  // ← ADD THIS

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // ← ADD THIS: Show loading indicator
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
            // ← MODIFIED: Parse M3U playlist with cache support
            long startTime = System.currentTimeMillis();  // ← ADD THIS: Measure load time
            channels = M3UParser.parse(PLAYLIST);
            long loadTime = System.currentTimeMillis() - startTime;  // ← ADD THIS

            if (channels == null) channels = new ArrayList<>();

            runOnUiThread(() -> {
                // ← ADD THIS: Hide loading indicator
                progressBar.setVisibility(View.GONE);

                // ← ADD THIS: Show load time (optional)
                Toast.makeText(MainActivity.this,
                        "Loaded " + channels.size() + " channels in " + loadTime + "ms",
                        Toast.LENGTH_SHORT).show();

                adapter = new ChannelAdapter(channels, channel -> {
                    // Launch PlayerActivity on channel click
                    Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                    intent.putExtra("url", channel.getUrl());
                    startActivity(intent);
                });

                recyclerView.setAdapter(adapter);
            });

        }).start();
    }
}