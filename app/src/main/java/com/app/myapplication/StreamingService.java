package com.app.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.Util;

import java.net.HttpURLConnection;
import java.net.URL;

public class StreamingService extends Service implements Player.Listener {

    private static final String CHANNEL_ID = "streaming_channel";
    private static final int NOTIFICATION_ID = 1001;

    private int retryCount = 0;
    private static final int MAX_RETRIES = 2;

    private ExoPlayer player;
    private String currentUrl;
    private final IBinder binder = new LocalBinder();
    private NotificationManager notificationManager;

    public class LocalBinder extends Binder {
        public StreamingService getService() {
            return StreamingService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initializePlayer();
        startForegroundWithNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            String url = intent.getStringExtra("url");

            // Handle notification actions
            if (action != null) {
                switch (action) {
                    case "PLAY":
                        if (player != null && !player.isPlaying()) {
                            player.play();
                            updateNotification("Playing");
                        }
                        break;
                    case "PAUSE":
                        if (player != null && player.isPlaying()) {
                            player.pause();
                            updateNotification("Paused");
                        }
                        break;
                    case "STOP":
                        stopPlayback();
                        break;
                }
            }

            // Handle new stream URL
            if (url != null && !url.equals(currentUrl)) {
                playStream(url);
                updateNotification("Loading stream...");
            }
        }
        return START_STICKY;
    }

    private void initializePlayer() {
        // ← MODIFIED: Reduced timeout values (10s → 3s)
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(Util.getUserAgent(this, "MyIPTVApp"))
                .setConnectTimeoutMs(3000)  // ← CHANGED: was 10000
                .setReadTimeoutMs(3000);     // ← CHANGED: was 10000

        // Build ExoPlayer (without custom LoadControl to avoid errors)
        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(com.google.android.exoplayer2.audio.AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .build();

        player.addListener(this);
    }

    private void playStream(String url) {
        if (player == null) return;

        currentUrl = url;

        // Stop current playback
        player.stop();

        // Create media item
        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(url)
                .build();

        // ← ADD THIS BLOCK: Optimized HLS loading
        if (url.contains(".m3u8")) {
            DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent(Util.getUserAgent(this, "MyIPTVApp"))
                    .setConnectTimeoutMs(3000)
                    .setReadTimeoutMs(3000);

            HlsMediaSource hlsMediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(true)  // ← KEY: Faster HLS startup
                    .createMediaSource(mediaItem);
            player.setMediaSource(hlsMediaSource);
        } else {
            player.setMediaItem(mediaItem);
        }

        // Prepare and play
        player.prepare();
        player.play();

        updateNotification("Streaming...");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Streaming Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows when streaming content is playing");
            channel.setSound(null, null);
            channel.setShowBadge(false);

            notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void startForegroundWithNotification() {
        Notification notification = createNotification("Initializing...");
        startForeground(NOTIFICATION_ID, notification);
    }

    private void updateNotification(String status) {
        Notification notification = createNotification(status);
        if (notificationManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.notify(NOTIFICATION_ID, notification);
            } else {
                // For older versions, use startForeground to update
                startForeground(NOTIFICATION_ID, notification);
            }
        }
    }

    private Notification createNotification(String status) {
        // Create intent for when notification is clicked
        Intent notificationIntent = new Intent(this, PlayerActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Create play/pause action
        boolean isPlaying = player != null && player.isPlaying();
        Intent playPauseIntent = new Intent(this, StreamingService.class);
        playPauseIntent.setAction(isPlaying ? "PAUSE" : "PLAY");
        PendingIntent playPausePendingIntent = PendingIntent.getService(this, 1, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Create stop action
        Intent stopIntent = new Intent(this, StreamingService.class);
        stopIntent.setAction("STOP");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 2, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("NECHATV Stream")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        // Add media controls
        builder.addAction(
                isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                isPlaying ? "Pause" : "Play",
                playPausePendingIntent
        );
        builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
        );

        // Add media style for better appearance
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1));
        }

        return builder.build();
    }

// Add these imports at the top


    // Replace the onPlayerError method with this:
    @Override
    public void onPlayerError(PlaybackException error) {
        error.printStackTrace();

        String errorMessage = "Connection failed";
        String errorType = "UNKNOWN";

        // Detect different error types
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) {
            errorMessage = "Network error - Please check your internet connection";
            errorType = "NETWORK_ERROR";
        } else if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) {
            errorMessage = "Connection timeout - Server is slow or offline";
            errorType = "TIMEOUT";
        } else if (error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE) {
            errorMessage = "This channel is geo-blocked and not available in your region";
            errorType = "GEO_BLOCKED";
        } else if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
            errorMessage = "Channel unavailable (HTTP error)";
            errorType = "HTTP_ERROR";

            // Try to detect if it's geo-blocked by checking the URL
            if (currentUrl != null) {
                checkIfGeoBlocked(currentUrl);
            }
        }

        updateNotification("Error: " + errorMessage);

        // Send error to activity
        Intent intent = new Intent("STREAMING_ERROR");
        intent.putExtra("error", errorMessage);
        intent.putExtra("errorType", errorType);
        sendBroadcast(intent);

        // Auto retry for network errors only
        if (errorType.equals("NETWORK_ERROR") && retryCount < MAX_RETRIES) {
            retryCount++;
            updateNotification("Retrying... (" + retryCount + "/" + MAX_RETRIES + ")");
            new android.os.Handler().postDelayed(() -> {
                if (player != null && currentUrl != null) {
                    playStream(currentUrl);
                }
            }, 2000);
        } else {
            retryCount = 0;
        }
    }

    // Add this method to check if URL is geo-blocked
    private void checkIfGeoBlocked(String url) {
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                int responseCode = connection.getResponseCode();
                connection.disconnect();

                if (responseCode == 403) {
                    Intent intent = new Intent("STREAMING_ERROR");
                    intent.putExtra("error", "This channel is geo-blocked");
                    intent.putExtra("errorType", "GEO_BLOCKED");
                    sendBroadcast(intent);
                }
            } catch (Exception e) {
                // Ignore
            }
        }).start();
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        String status;
        switch (playbackState) {
            case Player.STATE_BUFFERING:
                status = "Buffering...";
                break;
            case Player.STATE_READY:
                status = "Playing";
                break;
            case Player.STATE_ENDED:
                status = "Stream ended";
                break;
            case Player.STATE_IDLE:
                status = "Idle";
                break;
            default:
                status = "Loading...";
        }
        updateNotification(status);

        // Broadcast state changes to activity
        Intent intent = new Intent("PLAYBACK_STATE");
        intent.putExtra("state", playbackState);
        intent.putExtra("status", status);
        sendBroadcast(intent);
    }

    public ExoPlayer getPlayer() {
        return player;
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public void pausePlayback() {
        if (player != null && player.isPlaying()) {
            player.pause();
            updateNotification("Paused");
        }
    }

    public void resumePlayback() {
        if (player != null && !player.isPlaying()) {
            player.play();
            updateNotification("Playing");
        }
    }

    public void stopPlayback() {
        if (player != null) {
            player.stop();
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}