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
    private ProgressBar progressBar;

    private List<Channel> channels = new ArrayList<>();
    private ChannelAdapter adapter;

    private final String PLAYLIST = "https://iptv-org.github.io/iptv/index.m3u";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        searchView = findViewById(R.id.searchView);
        progressBar = findViewById(R.id.progressBar);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        progressBar.setVisibility(View.VISIBLE);

        loadChannels();

        // 🔍 Search functionality
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
            List<Channel> result = M3UParser.parse(PLAYLIST);

            if (result == null) result = new ArrayList<>();

            List<Channel> finalResult = result;

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);

                channels = finalResult;

                adapter = new ChannelAdapter(channels, channel -> {

                    // ✅ SAFE CLICK HANDLING
                    if (channel.getUrl() == null || channel.getUrl().isEmpty()) {
                        Toast.makeText(MainActivity.this,
                                "Invalid channel", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                        intent.putExtra("url", channel.getUrl());
                        intent.putExtra("name", channel.getName());
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this,
                                "Cannot open player", Toast.LENGTH_SHORT).show();
                    }
                });

                recyclerView.setAdapter(adapter);

                Toast.makeText(MainActivity.this,
                        "Loaded " + channels.size() + " channels",
                        Toast.LENGTH_SHORT).show();
            });

        }).start();
    }
}