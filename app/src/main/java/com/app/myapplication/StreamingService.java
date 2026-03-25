package com.app.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
    public static final String ACTION_PLAY = "ACTION_PLAY";
    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_STOP = "ACTION_STOP";
    private static final String CHANNEL_ID = "streaming_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int MAX_RETRIES = 2;

    private ExoPlayer player;
    private String currentUrl;
    private int retryCount = 0;
    private final IBinder binder = new LocalBinder();
    private NotificationManager notificationManager;
    private Handler mainHandler = new Handler();

    public class LocalBinder extends Binder {
        public StreamingService getService() {
            return StreamingService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initPlayer();
        startForegroundWithNotification("Initializing...");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {

            String action = intent.getAction();

            if (ACTION_PLAY.equals(action)) {
                resumePlayback();
                return START_STICKY;
            }

            if (ACTION_PAUSE.equals(action)) {
                pausePlayback();
                return START_STICKY;
            }

            if (ACTION_STOP.equals(action)) {
                stopPlayback();
                return START_NOT_STICKY;
            }

            String url = intent.getStringExtra("url");
            if (url != null && !url.equals(currentUrl)) {
                playStreamAsync(url);
            }
        }

        return START_STICKY;
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(com.google.android.exoplayer2.audio.AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .build();
        player.addListener(this);
    }

    private void playStreamAsync(String url) {
        currentUrl = url;
        if (player == null) return;

        updateNotification("Loading stream...");
        player.stop();

        new Thread(() -> {
            try {
                preConnect(url); // Non-blocking network pre-check
            } catch (Exception ignored) {}

            mainHandler.post(() -> {
                MediaItem mediaItem = MediaItem.fromUri(url);
                if (url.endsWith(".m3u8")) {
                    HlsMediaSource hls = new HlsMediaSource.Factory(
                            new DefaultHttpDataSource.Factory()
                                    .setUserAgent(Util.getUserAgent(this, "IPTVFast"))
                                    .setConnectTimeoutMs(5000)
                                    .setReadTimeoutMs(5000)
                    ).createMediaSource(mediaItem);
                    player.setMediaSource(hls);
                } else {
                    player.setMediaItem(mediaItem);
                }

                player.prepare();
                player.play();
                updateNotification("Streaming...");
            });
        }).start();
    }

    private void preConnect(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("HEAD");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        conn.connect();
        conn.disconnect();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Streaming Service",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Shows when streaming content is playing");
            notificationManager = getSystemService(NotificationManager.class);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC); // Show on lock screen
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void startForegroundWithNotification(String status) {
        Notification notification = createNotification(status);
        startForeground(NOTIFICATION_ID, notification);
    }

    private void updateNotification(String status) {
        Notification notification = createNotification(status);
        startForeground(NOTIFICATION_ID, notification); //  IMPORTANT FIX
    }

    private Notification createNotification(String status) {

        Intent intent = new Intent(this, PlayerActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // PLAY intent
        Intent playIntent = new Intent(this, StreamingService.class);
        playIntent.setAction(ACTION_PLAY);
        PendingIntent playPendingIntent = PendingIntent.getService(
                this, 1, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // PAUSE intent
        Intent pauseIntent = new Intent(this, StreamingService.class);
        pauseIntent.setAction(ACTION_PAUSE);
        PendingIntent pausePendingIntent = PendingIntent.getService(
                this, 2, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // STOP intent
        Intent stopIntent = new Intent(this, StreamingService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 3, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("NECHATV Stream")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1) .setMediaSession(null));
                ;


        //  DYNAMIC BUTTON (KEY FIX)
        if (isPlaying()) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent);
            builder.setSmallIcon(android.R.drawable.ic_media_play);
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "Play", playPendingIntent);
        }

        // Always add stop
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent);

        return builder.build();
    }

    // ExoPlayer listener
    @Override
    public void onPlayerError(PlaybackException error) {
        String errorMessage = "Unknown error";
        String errorType = "UNKNOWN";

        switch (error.errorCode) {
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
                errorMessage = "Network error";
                errorType = "NETWORK_ERROR";
                break;
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                errorMessage = "Connection timeout";
                errorType = "TIMEOUT";
                break;
            case PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE:
                errorMessage = "Geo-blocked channel";
                errorType = "GEO_BLOCKED";
                break;
        }

        updateNotification("Error: " + errorMessage);

        Intent intent = new Intent("STREAMING_ERROR");
        intent.putExtra("error", errorMessage);
        intent.putExtra("errorType", errorType);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        // Retry logic
        if (retryCount < MAX_RETRIES) {
            retryCount++;
            mainHandler.postDelayed(() -> playStreamAsync(currentUrl), 2000);
        } else {
            retryCount = 0;
        }
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        String status;
        switch (state) {
            case Player.STATE_BUFFERING: status = "Buffering..."; break;
            case Player.STATE_READY: status = "Playing"; break;
            case Player.STATE_ENDED: status = "Stream ended"; break;
            default: status = "Loading..."; break;
        }
        updateNotification(status);

        Intent intent = new Intent("PLAYBACK_STATE");
        intent.putExtra("state", state);
        intent.putExtra("status", status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public ExoPlayer getPlayer() { return player; }
    public boolean isPlaying() { return player != null && player.isPlaying(); }

    public void pausePlayback() {
        if (player != null) player.pause();
        updateNotification("Paused");

    }
    public void resumePlayback() {
        if (player != null)
            player.play();
        updateNotification("Playing");
    }
    public void stopPlayback() { if (player != null) player.stop(); stopForeground(true); stopSelf(); }

    @Override
    public void onDestroy() {
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }
}