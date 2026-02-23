package com.app.myapplication;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class M3UParser {

    public static List<Channel> parse(String playlistUrl) {
        List<Channel> channels = new ArrayList<>();

        try {
            URL url = new URL(playlistUrl);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(url.openStream())
            );

            String line;
            String channelName = "";

            while ((line = reader.readLine()) != null) {

                if (line.startsWith("#EXTINF")) {
                    int commaIndex = line.indexOf(",");
                    channelName = line.substring(commaIndex + 1);
                }
                else if (line.startsWith("http")) {
                    channels.add(new Channel(channelName, line));
                }
            }

            reader.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return channels;
    }
}
