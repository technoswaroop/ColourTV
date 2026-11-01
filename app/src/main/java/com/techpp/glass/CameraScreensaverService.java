package com.techpp.glass;

import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.service.dreams.DreamService;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;

import java.io.IOException;
import java.security.GeneralSecurityException;

public final class CameraScreensaverService extends DreamService {
    private static final String TAG = "CameraScreensaver";
    private static final int CAMERA_COUNT = 3;

    private final TextureView[] videos = new TextureView[CAMERA_COUNT];
    private final Surface[] surfaces = new Surface[CAMERA_COUNT];
    private final ExoPlayer[] players = new ExoPlayer[CAMERA_COUNT];
    private final TextView[] statuses = new TextView[CAMERA_COUNT];
    private final TextView[] liveBadges = new TextView[CAMERA_COUNT];
    private CameraPanel.Config[] configs;
    private boolean settingsUnavailable;
    private boolean dreaming;
    private float density;

    public CameraScreensaverService() { }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        density = getResources().getDisplayMetrics().density;
        try {
            configs = CameraPanel.loadSavedConfigs(this);
        } catch (IOException | GeneralSecurityException | org.json.JSONException | IllegalArgumentException e) {
            settingsUnavailable = true;
            Log.e(TAG, "Could not load encrypted camera settings");
        }
        setContentView(createContentView());
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        dreaming = true;
        for (int i = 0; i < CAMERA_COUNT; i++) startPlayer(i);
    }

    @Override
    public void onDreamingStopped() {
        dreaming = false;
        for (int i = 0; i < CAMERA_COUNT; i++) stopPlayer(i);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        dreaming = false;
        for (int i = 0; i < CAMERA_COUNT; i++) {
            stopPlayer(i);
            if (surfaces[i] != null) {
                surfaces[i].release();
                surfaces[i] = null;
            }
            videos[i] = null;
            statuses[i] = null;
            liveBadges[i] = null;
        }
        super.onDetachedFromWindow();
    }

    private View createContentView() {
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.BLACK);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), 0, dp(16), dp(16));
        screen.addView(row, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout rightColumn = new LinearLayout(this);
        rightColumn.setOrientation(LinearLayout.VERTICAL);

        View topScrim = new View(this);
        topScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0x00000000}));
        screen.addView(topScrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(104), Gravity.TOP));

        TextView heading = text("HOME CAMERA LIVE FEED", 16, true);
        FrameLayout.LayoutParams headingParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, dp(44), Gravity.TOP | Gravity.START);
        headingParams.setMargins(dp(24), dp(20), 0, 0);
        screen.addView(heading, headingParams);

        for (int i = 0; i < CAMERA_COUNT; i++) {
            FrameLayout frame = new FrameLayout(this);
            frame.setBackground(rounded(0xFF17191E, 0x55FFFFFF));
            frame.setClipToOutline(true);
            if (i == 0) {
                row.addView(frame, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            } else {
                if (i == 1) {
                    LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                    columnParams.setMarginStart(dp(10));
                    row.addView(rightColumn, columnParams);
                }
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
                if (i == 2) tileParams.topMargin = dp(8);
                rightColumn.addView(frame, tileParams);
            }

            TextureView video = new TextureView(this);
            videos[i] = video;
            frame.addView(video, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            final int camera = i;
            video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                    surfaces[camera] = new Surface(texture);
                    startPlayer(camera);
                }

                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                    stopPlayer(camera);
                    if (surfaces[camera] != null) {
                        surfaces[camera].release();
                        surfaces[camera] = null;
                    }
                    return true;
                }

                @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
            });

            TextView label = text(configs != null && configs[i] != null
                    ? configs[i].name : CameraPanel.defaultCameraName(i), 14, true);
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setPadding(dp(10), 0, dp(10), 0);
            label.setBackgroundColor(0xB8000000);
            frame.addView(label, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, dp(36), Gravity.BOTTOM));

            TextView live = text("● Live", 12, true);
            live.setTextColor(0xFFFF3B30);
            live.setGravity(Gravity.CENTER);
            live.setPadding(dp(9), 0, dp(9), 0);
            live.setBackground(rounded(0xB8000000, 0x66FF3B30));
            live.setVisibility(View.GONE);
            FrameLayout.LayoutParams liveParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, dp(28), Gravity.BOTTOM | Gravity.END);
            liveParams.setMargins(0, 0, dp(6), dp(4));
            frame.addView(live, liveParams);
            liveBadges[i] = live;

            TextView status = text(initialStatus(i), 14, true);
            status.setGravity(Gravity.CENTER);
            status.setPadding(dp(12), dp(8), dp(12), dp(8));
            status.setBackground(rounded(0xA9000000, 0));
            FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            statusParams.setMargins(dp(8), dp(44), dp(8), dp(8));
            frame.addView(status, statusParams);
            statuses[i] = status;
        }
        return screen;
    }

    private String initialStatus(int index) {
        if (settingsUnavailable) return "Camera settings unavailable · open Talkies to reconfigure";
        if (configs == null || configs[index] == null) return "Set up this camera in Talkies";
        return "Connecting to camera…";
    }

    private void startPlayer(int index) {
        if (!dreaming || configs == null || configs[index] == null || surfaces[index] == null
                || players[index] != null) return;
        CameraPanel.Config config = configs[index];
        String path = config.lowBandwidth ? "stream2" : "stream1";
        String address = "rtsp://" + Uri.encode(config.username) + ":" + Uri.encode(config.password)
                + "@" + config.host + ":554/" + path;
        ExoPlayer player = new ExoPlayer.Builder(getApplicationContext()).build();
        players[index] = player;
        setStatus(index, "Connecting to camera…");
        try {
            RtspMediaSource source = new RtspMediaSource.Factory()
                    .setForceUseRtpTcp(true)
                    .createMediaSource(MediaItem.fromUri(address));
            player.setVideoSurface(surfaces[index]);
            player.setVolume(0f);
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (players[index] != player) return;
                    if (state == Player.STATE_READY) setStatus(index, "LIVE");
                    else if (state == Player.STATE_BUFFERING) setStatus(index, "Connecting / buffering…");
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    if (players[index] != player) return;
                    boolean authenticationFailed = CameraPanel.isAuthenticationFailure(error);
                    Log.w(TAG, "Camera stream failed for slot " + (index + 1)
                            + " (" + error.getErrorCodeName() + ")");
                    stopPlayer(index);
                    setStatus(index, authenticationFailed
                            ? "Authentication failed · check Tapo Camera Account"
                            : "Stream unavailable · check RTSP and network");
                }
            });
            player.setMediaSource(source);
            player.prepare();
            player.play();
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not start camera stream for slot " + (index + 1)
                    + " (" + e.getClass().getSimpleName() + ")");
            stopPlayer(index);
            setStatus(index, "Could not start · check camera settings");
        }
    }

    private void stopPlayer(int index) {
        ExoPlayer player = players[index];
        players[index] = null;
        if (player != null) {
            player.clearVideoSurface();
            player.stop();
            player.release();
        }
    }

    private void setStatus(int index, String message) {
        TextView status = statuses[index];
        TextView badge = liveBadges[index];
        boolean live = "LIVE".equals(message);
        if (status != null) {
            status.setText(message);
            status.setVisibility(live ? View.GONE : View.VISIBLE);
        }
        if (badge != null) badge.setVisibility(live ? View.VISIBLE : View.GONE);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        text.setTextColor(Color.WHITE);
        text.setTypeface(android.graphics.Typeface.DEFAULT, bold
                ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return text;
    }

    private GradientDrawable rounded(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(10));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * density);
    }
}
