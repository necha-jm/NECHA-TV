package com.app.myapplication;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class M3UParser {

    // ← ADD THIS: Cache for faster subsequent loads
    private static List<Channel> cachedChannels = null;
    private static long cacheTime = 0;
    private static final long CACHE_DURATION = 5 * 60 * 1000; // 5 minutes cache

    public static List<Channel> parse(String playlistUrl) {

        // ← ADD THIS: Return cached data if still fresh
        if (cachedChannels != null && (System.currentTimeMillis() - cacheTime) < CACHE_DURATION) {
            return cachedChannels;
        }

        List<Channel> channels = new ArrayList<>();
        HttpURLConnection connection = null;

        try {
            URL url = new URL(playlistUrl);
            connection = (HttpURLConnection) url.openConnection();

            // ← ADD THESE LINES: Faster connection settings
            connection.setConnectTimeout(5000);  // 5 seconds timeout
            connection.setReadTimeout(5000);     // 5 seconds timeout
            connection.setRequestProperty("User-Agent", "IPTVApp/1.0");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream())
            );

            String line;
            String channelName = "";

            while ((line = reader.readLine()) != null) {

                if (line.startsWith("#EXTINF")) {
                    int commaIndex = line.indexOf(",");
                    if (commaIndex != -1) {
                        channelName = line.substring(commaIndex + 1);
                        // ← ADD THIS: Clean up channel name (remove extra spaces)
                        channelName = channelName.trim();
                    }
                }
                else if (line.startsWith("http")) {
                    // ← ADD THIS: Skip empty channel names
                    if (channelName != null && !channelName.isEmpty()) {
                        channels.add(new Channel(channelName, line.trim()));
                    }
                    channelName = ""; // Reset for next channel
                }
            }

            reader.close();

            // ← ADD THIS: Close connection properly
            if (connection != null) {
                connection.disconnect();
            }

            // ← ADD THIS: Cache the results
            cachedChannels = channels;
            cacheTime = System.currentTimeMillis();

        } catch (Exception e) {
            e.printStackTrace();
            // ← ADD THIS: Return cached data if network fails
            if (cachedChannels != null) {
                return cachedChannels;
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return channels;
    }

    // ← ADD THIS: Method to clear cache if needed
    public static void clearCache() {
        cachedChannels = null;
        cacheTime = 0;
    }
}