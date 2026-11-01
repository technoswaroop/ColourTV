package com.techpp.glass;

import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.app.Dialog;
import android.app.AlertDialog;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;

/** Three local Tapo RTSP feeds, kept private to the TV and encrypted with its Android Keystore. */
final class CameraPanel extends LinearLayout {
    private static final String TAG = "GlassCamera";
    private static final String KEY_ALIAS = "talkies-tapo-camera-settings";
    private static final int CAMERA_COUNT = 3;
    private static final String[] NAMES = {"Tapo C210 · 1", "Tapo C210 · 2", "Tapo C310"};

    interface ConfigureListener { void onConfigure(int camera); }

    static final class Config {
        final String name, host, username, password;
        final boolean lowBandwidth;

        Config(String name, String host, String username, String password, boolean lowBandwidth) {
            this.name = name;
            this.host = host;
            this.username = username;
            this.password = password;
            this.lowBandwidth = lowBandwidth;
        }
    }

    private static final class Tile {
        final FrameLayout frame;
        final TextureView video;
        final TextView label, status, action, liveBadge;
        final ObjectAnimator liveBlink;
        Config config;
        Surface surface;
        ExoPlayer player;
        boolean surfaceReady;

        Tile(FrameLayout frame, TextureView video, TextView label, TextView status, TextView action,
             TextView liveBadge) {
            this.frame = frame;
            this.video = video;
            this.label = label;
            this.status = status;
            this.action = action;
            this.liveBadge = liveBadge;
            liveBlink = blink(liveBadge);
        }
    }

    private final Context context;
    private final float density;
    private final Tile[] tiles = new Tile[CAMERA_COUNT];
    private final android.util.AtomicFile settingsFile;
    private ConfigureListener configureListener;
    private boolean streamsVisible;
    private String settingsError;
    private TextView fullscreenButton;
    private Dialog fullscreenDialog;
    private Tile fullscreenTile;
    private Surface fullscreenSurface;
    private TextureView fullscreenVideo;
    private TextView fullscreenStatus;
    private TextView fullscreenSettings;
    private TextView fullscreenHeading;
    private TextView fullscreenLiveBadge;
    private ObjectAnimator fullscreenLiveBlink;
    private boolean fullscreenAll;
    private final TextureView[] fullscreenTiles = new TextureView[CAMERA_COUNT];
    private final Surface[] fullscreenSurfaces = new Surface[CAMERA_COUNT];
    private final TextView[] fullscreenStatuses = new TextView[CAMERA_COUNT];
    private final TextView[] fullscreenBadges = new TextView[CAMERA_COUNT];
    private final TextView[] fullscreenLabels = new TextView[CAMERA_COUNT];
    private final ObjectAnimator[] fullscreenBlinks = new ObjectAnimator[CAMERA_COUNT];

    CameraPanel(Context context) {
        super(context);
        this.context = context.getApplicationContext();
        density = context.getResources().getDisplayMetrics().density;
        settingsFile = new android.util.AtomicFile(new File(context.getNoBackupFilesDir(), "camera-streams.bin"));
        setOrientation(VERTICAL);
        setPadding(dp(10), dp(8), dp(10), dp(8));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, dp(28)));
        TextView heading = text("HOME CAMERA LIVE FEED", 12, true);
        heading.setLetterSpacing(0.06f);
        header.addView(heading, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        fullscreenButton = text("FULLSCREEN", 10, true);
        fullscreenButton.setFocusable(true);
        fullscreenButton.setFocusableInTouchMode(true);
        fullscreenButton.setGravity(Gravity.CENTER);
        fullscreenButton.setPadding(dp(13), 0, dp(13), 0);
        fullscreenButton.setBackground(rounded(0xB8000000, 0x55FFFFFF, 10));
        fullscreenButton.setOnFocusChangeListener((view, focused) ->
                fullscreenButton.setBackground(rounded(focused ? 0xFF4A5260 : 0xB8000000,
                        focused ? 0xFFFFFFFF : 0x55FFFFFF, 10)));
        fullscreenButton.setOnClickListener(view -> openFullscreenAll());
        header.addView(fullscreenButton, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(26)));
        TextView help = text("Local RTSP streams · camera and TV must be on the same home network", 10, false);
        LayoutParams helpParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(18));
        helpParams.bottomMargin = dp(4);
        addView(help, helpParams);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        for (int i = 0; i < CAMERA_COUNT; i++) {
            Tile tile = createTile(context, i);
            tiles[i] = tile;
            LayoutParams params = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) params.setMarginStart(dp(8));
            row.addView(tile.frame, params);
        }
        loadSettings();
        for (Tile tile : tiles) updateTile(tile);
    }

    void setConfigureListener(ConfigureListener listener) {
        configureListener = listener;
    }

    View tile(int index) { return tiles[index].frame; }
    View fullscreenButton() { return fullscreenButton; }
    Config config(int index) { return tiles[index].config; }
    String cameraName(int index) {
        Tile tile = tiles[index];
        Config config = tile == null ? null : tile.config;
        return config == null || config.name.trim().isEmpty() ? defaultCameraName(index) : config.name;
    }

    static String defaultCameraName(int index) { return NAMES[index]; }

    void openFullscreen(int index) {
        Tile tile = tiles[index];
        if (tile.config == null || fullscreenDialog != null) return;

        Dialog dialog = new Dialog(getContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    if (fullscreenSettings != null && fullscreenSettings.hasFocus()
                            && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (fullscreenVideo != null) fullscreenVideo.requestFocus();
                        return true;
                    }
                    if (fullscreenSettings != null && !fullscreenSettings.hasFocus()
                            && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                            || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                        fullscreenSettings.requestFocus();
                        return true;
                    }
                }
                return super.dispatchKeyEvent(event);
            }
        };
        fullscreenDialog = dialog;
        fullscreenTile = tile;
        fullscreenAll = false;

        FrameLayout screen = new FrameLayout(getContext());
        screen.setBackgroundColor(Color.BLACK);
        TextureView video = new TextureView(getContext());
        video.setFocusable(true);
        video.setFocusableInTouchMode(true);
        fullscreenVideo = video;
        screen.addView(video, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout controls = new LinearLayout(getContext());
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(20), dp(12), dp(20), dp(12));
        controls.setBackgroundColor(0xB8000000);
        TextView heading = text(cameraName(index), 16, true);
        fullscreenHeading = heading;
        controls.addView(heading, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView liveBadge = liveBadge();
        controls.addView(liveBadge, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)));
        fullscreenLiveBadge = liveBadge;
        fullscreenLiveBlink = blink(liveBadge);
        setLive(liveBadge, fullscreenLiveBlink, "LIVE".equals(tile.status.getText().toString()));
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(44));
        settingsParams.setMarginStart(dp(12));
        TextView settings = text("⚙  SETTINGS", 14, true);
        settings.setFocusable(true);
        settings.setFocusableInTouchMode(true);
        settings.setGravity(Gravity.CENTER);
        settings.setPadding(dp(18), dp(10), dp(18), dp(10));
        settings.setBackground(rounded(0xFF30343B, 0x55FFFFFF, 10));
        settings.setOnFocusChangeListener((view, focused) ->
                settings.setBackground(rounded(focused ? 0xFF4A5260 : 0xFF30343B,
                        focused ? 0xFFFFFFFF : 0x55FFFFFF, 10)));
        settings.setOnClickListener(view -> {
            if (configureListener != null) configureListener.onConfigure(index);
        });
        controls.addView(settings, settingsParams);
        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT, Gravity.TOP);
        screen.addView(controls, controlsParams);
        fullscreenSettings = settings;

        TextView status = text(String.valueOf(tile.status.getText()), 20, true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(18), dp(12), dp(18), dp(12));
        status.setBackground(rounded(0xA9000000, 0, 8));
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        screen.addView(status, statusParams);
        fullscreenStatus = status;
        updateFullscreenStatus(tile.status.getText().toString());

        video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                if (fullscreenDialog != dialog || fullscreenTile != tile) return;
                fullscreenSurface = new Surface(texture);
                if (tile.player != null) tile.player.setVideoSurface(fullscreenSurface);
                video.requestFocus();
            }

            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                restoreTileSurface(tile);
                return true;
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
        });

        dialog.setContentView(screen);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.black);
        }
        dialog.setOnDismissListener(ignored -> {
            restoreTileSurface(tile);
            fullscreenDialog = null;
            fullscreenTile = null;
            fullscreenVideo = null;
            fullscreenStatus = null;
            fullscreenSettings = null;
            fullscreenHeading = null;
            if (fullscreenLiveBlink != null) fullscreenLiveBlink.cancel();
            fullscreenLiveBlink = null;
            fullscreenLiveBadge = null;
        });
        dialog.show();
        if (window != null) window.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    void openFullscreenAll() {
        if (fullscreenDialog != null) return;
        Dialog dialog = new Dialog(getContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    if (fullscreenSettings != null && fullscreenSettings.hasFocus()
                            && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN
                            || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT
                            || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                        if (fullscreenTiles[0] != null) fullscreenTiles[0].requestFocus();
                        return true;
                    }
                    if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP && fullscreenSettings != null) {
                        for (TextureView tile : fullscreenTiles) {
                            if (tile != null && tile.hasFocus()) {
                                fullscreenSettings.requestFocus();
                                return true;
                            }
                        }
                    }
                }
                return super.dispatchKeyEvent(event);
            }
        };
        fullscreenDialog = dialog;
        fullscreenTile = null;
        fullscreenAll = true;

        FrameLayout screen = new FrameLayout(getContext());
        screen.setBackgroundColor(Color.BLACK);
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setPadding(dp(16), 0, dp(16), dp(16));
        FrameLayout.LayoutParams rowParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        screen.addView(row, rowParams);
        LinearLayout rightColumn = new LinearLayout(getContext());
        rightColumn.setOrientation(VERTICAL);

        View topScrim = new View(getContext());
        topScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0x00000000}));
        screen.addView(topScrim, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(104), Gravity.TOP));

        fullscreenSettings = text("⚙  SETTINGS", 14, true);
        fullscreenSettings.setFocusable(true);
        fullscreenSettings.setFocusableInTouchMode(true);
        fullscreenSettings.setGravity(Gravity.CENTER);
        fullscreenSettings.setPadding(dp(18), dp(10), dp(18), dp(10));
        fullscreenSettings.setBackground(rounded(0xFF30343B, 0x55FFFFFF, 10));
        fullscreenSettings.setOnFocusChangeListener((view, focused) ->
                fullscreenSettings.setBackground(rounded(focused ? 0xFF4A5260 : 0xFF30343B,
                        focused ? 0xFFFFFFFF : 0x55FFFFFF, 10)));
        fullscreenSettings.setOnClickListener(view -> showCameraSettingsChooser());
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(44),
                Gravity.TOP | Gravity.END);
        settingsParams.setMargins(0, dp(20), dp(20), 0);
        screen.addView(fullscreenSettings, settingsParams);

        TextView heading = text("HOME CAMERA LIVE FEED", 16, true);
        FrameLayout.LayoutParams headingParams = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(44),
                Gravity.TOP | Gravity.START);
        headingParams.setMargins(dp(24), dp(20), 0, 0);
        screen.addView(heading, headingParams);

        for (int i = 0; i < CAMERA_COUNT; i++) {
            final int camera = i;
            FrameLayout frame = new FrameLayout(getContext());
            frame.setBackground(rounded(0xFF17191E, 0x55FFFFFF, 10));
            frame.setClipToOutline(true);
            if (i == 0) {
                row.addView(frame, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
            } else {
                if (i == 1) {
                    LayoutParams columnParams = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
                    columnParams.setMarginStart(dp(10));
                    row.addView(rightColumn, columnParams);
                }
                LayoutParams tileParams = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f);
                if (i == 2) tileParams.topMargin = dp(8);
                rightColumn.addView(frame, tileParams);
            }

            TextureView video = new TextureView(getContext());
            video.setFocusable(true);
            video.setFocusableInTouchMode(true);
            fullscreenTiles[i] = video;
            frame.addView(video, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            TextView label = text(cameraName(i), 14, true);
            fullscreenLabels[i] = label;
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setPadding(dp(10), 0, dp(10), 0);
            label.setBackgroundColor(0xB8000000);
            FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(36),
                    Gravity.BOTTOM);
            frame.addView(label, labelParams);

            TextView live = liveBadge();
            FrameLayout.LayoutParams liveParams = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(28),
                    Gravity.BOTTOM | Gravity.END);
            liveParams.setMargins(0, 0, dp(6), dp(4));
            frame.addView(live, liveParams);
            fullscreenBadges[i] = live;
            fullscreenBlinks[i] = blink(live);
            setLive(live, fullscreenBlinks[i], "LIVE".equals(tiles[i].status.getText().toString()));

            TextView status = text(String.valueOf(tiles[i].status.getText()), 14, true);
            status.setGravity(Gravity.CENTER);
            status.setPadding(dp(12), dp(8), dp(12), dp(8));
            status.setBackground(rounded(0xA9000000, 0, 8));
            FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            statusParams.setMargins(dp(8), dp(44), dp(8), dp(8));
            frame.addView(status, statusParams);
            fullscreenStatuses[i] = status;
            updateFullscreenAllStatus(i, tiles[i].status.getText().toString());

            video.setOnFocusChangeListener((view, focused) -> frame.setBackground(
                    rounded(0xFF17191E, focused ? 0xFFFFFFFF : 0x55FFFFFF, 10)));
            video.setOnClickListener(view -> {
                if (tiles[camera].config == null && configureListener != null) configureListener.onConfigure(camera);
            });
            video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                    if (fullscreenDialog != dialog || !fullscreenAll) return;
                    Surface surface = new Surface(texture);
                    fullscreenSurfaces[camera] = surface;
                    ExoPlayer player = tiles[camera].player;
                    if (player != null) player.setVideoSurface(surface);
                }

                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { }
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                    restoreTileSurface(camera);
                    return true;
                }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
            });
        }

        dialog.setContentView(screen);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.black);
        }
        dialog.setOnDismissListener(ignored -> clearFullscreenAll());
        dialog.show();
        if (window != null) window.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    private void showCameraSettingsChooser() {
        String[] names = new String[CAMERA_COUNT];
        for (int i = 0; i < CAMERA_COUNT; i++)
            names[i] = cameraName(i) + (tiles[i].config == null ? " · Set up" : " · Settings");
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Camera settings")
                .setItems(names, (dialog, index) -> {
                    if (configureListener != null) configureListener.onConfigure(index);
                })
                .show();
    }

    private void clearFullscreenAll() {
        for (int i = 0; i < CAMERA_COUNT; i++) {
            restoreTileSurface(i);
            fullscreenTiles[i] = null;
            fullscreenStatuses[i] = null;
            fullscreenBadges[i] = null;
            fullscreenLabels[i] = null;
            if (fullscreenBlinks[i] != null) fullscreenBlinks[i].cancel();
            fullscreenBlinks[i] = null;
        }
        fullscreenSettings = null;
        fullscreenDialog = null;
        fullscreenAll = false;
    }

    void closeFullscreen() {
        if (fullscreenDialog != null) fullscreenDialog.dismiss();
    }

    void setStreamVisible(boolean visible) {
        streamsVisible = visible;
        for (Tile tile : tiles) {
            if (visible) startIfReady(tile);
            else {
                stop(tile);
                if (tile.config != null) setStatus(tile, "Stream paused · press OK to reconnect");
            }
        }
    }

    void saveConfig(int index, Config config) throws GeneralSecurityException, IOException, JSONException {
        Config previous = tiles[index].config;
        stop(tiles[index]);
        tiles[index].config = config;
        settingsError = null;
        try {
            persistSettings();
        } catch (GeneralSecurityException | IOException | JSONException e) {
            tiles[index].config = previous;
            if (streamsVisible) startIfReady(tiles[index]);
            throw e;
        }
        updateTile(tiles[index]);
        if (streamsVisible) startIfReady(tiles[index]);
    }

    void removeConfig(int index) throws GeneralSecurityException, IOException, JSONException {
        Config previous = tiles[index].config;
        tiles[index].config = null;
        settingsError = null;
        try {
            persistSettings();
        } catch (GeneralSecurityException | IOException | JSONException e) {
            tiles[index].config = previous;
            throw e;
        }
        stop(tiles[index]);
        updateTile(tiles[index]);
    }

    void releaseStreams() {
        streamsVisible = false;
        for (Tile tile : tiles) {
            stop(tile);
            if (tile.config != null) setStatus(tile, "Stream paused · press OK to reconnect");
        }
    }

    private Tile createTile(Context context, int index) {
        FrameLayout frame = new FrameLayout(context);
        frame.setBackground(rounded(0xFF17191E, 0x55FFFFFF, 10));
        frame.setClipToOutline(true);

        TextureView video = new TextureView(context);
        video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                Tile tile = tiles[index];
                tile.surface = new Surface(texture);
                tile.surfaceReady = true;
                startIfReady(tile);
            }

            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) { }

            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                Tile tile = tiles[index];
                tile.surfaceReady = false;
                stop(tile);
                if (tile.config != null) setStatus(tile, "Ready when camera tile is visible");
                if (tile.surface != null) {
                    tile.surface.release();
                    tile.surface = null;
                }
                return true;
            }

            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
        });
        frame.addView(video, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        TextView label = text(cameraName(index), 11, true);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp(8), 0, dp(8), 0);
        label.setBackgroundColor(0xB8000000);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(26), Gravity.TOP);
        frame.addView(label, labelParams);

        TextView status = text("Set up local camera stream", 10, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), dp(4), dp(8), dp(4));
        status.setBackground(rounded(0xA9000000, 0, 6));
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        statusParams.setMargins(dp(8), dp(30), dp(8), dp(30));
        frame.addView(status, statusParams);

        TextView liveBadge = liveBadge();
        FrameLayout.LayoutParams liveParams = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(24),
                Gravity.TOP | Gravity.END);
        liveParams.setMargins(0, dp(5), dp(7), 0);
        frame.addView(liveBadge, liveParams);

        TextView action = text("SET UP", 10, true);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(8), 0, dp(8), 0);
        action.setBackgroundColor(0xB8000000);
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(25), Gravity.BOTTOM);
        frame.addView(action, actionParams);
        frame.setOnClickListener(view -> {
            if (tiles[index].config == null) {
                if (configureListener != null) configureListener.onConfigure(index);
            } else openFullscreen(index);
        });
        action.setOnClickListener(view -> {
            if (configureListener != null) configureListener.onConfigure(index);
        });
        return new Tile(frame, video, label, status, action, liveBadge);
    }

    private void updateTile(Tile tile) {
        int index = indexOf(tile);
        if (settingsError != null) {
            setStatus(tile, "Camera settings unavailable · press OK to reconfigure");
            tile.action.setText("RECONFIGURE");
        } else if (tile.config == null) {
            setStatus(tile, "Set up RTSP in the Tapo app, then press OK");
            tile.action.setText("SET UP");
        } else if (tile.player == null) {
            setStatus(tile, tile.config.host + " · press OK to edit");
            tile.action.setText("CAMERA SETTINGS");
        }
        tile.label.setText(cameraName(index));
        if (fullscreenTile == tile && fullscreenHeading != null)
            fullscreenHeading.setText(cameraName(index));
        if (fullscreenAll && fullscreenLabels[index] != null)
            fullscreenLabels[index].setText(cameraName(index));
    }

    private int indexOf(Tile tile) {
        for (int i = 0; i < tiles.length; i++) if (tiles[i] == tile) return i;
        return -1;
    }

    private void startIfReady(Tile tile) {
        if (!streamsVisible || !tile.surfaceReady || tile.surface == null || tile.config == null || tile.player != null) return;
        Config config = tile.config;
        String path = config.lowBandwidth ? "stream2" : "stream1";
        String address = "rtsp://" + Uri.encode(config.username) + ":" + Uri.encode(config.password)
                + "@" + config.host + ":554/" + path;
        ExoPlayer player = new ExoPlayer.Builder(context).build();
        tile.player = player;
        setStatus(tile, "Connecting to camera…");
        tile.action.setText("CAMERA SETTINGS");
        try {
            RtspMediaSource source = new RtspMediaSource.Factory()
                    .setForceUseRtpTcp(true)
                    .createMediaSource(MediaItem.fromUri(address));
            Surface output = fullscreenAll && fullscreenSurfaces[indexOf(tile)] != null
                    ? fullscreenSurfaces[indexOf(tile)]
                    : fullscreenTile == tile && fullscreenSurface != null ? fullscreenSurface : tile.surface;
            player.setVideoSurface(output);
            player.setVolume(0f);
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int playbackState) {
                    if (tile.player != player) return;
                    if (playbackState == Player.STATE_READY) setStatus(tile, "LIVE");
                    else if (playbackState == Player.STATE_BUFFERING) setStatus(tile, "Connecting / buffering…");
                    else if (playbackState == Player.STATE_ENDED) {
                        stop(tile);
                        setStatus(tile, "Stream ended · press OK to retry");
                    }
                }

                @Override public void onPlayerError(PlaybackException error) {
                    if (tile.player != player) return;
                    boolean authenticationFailed = isAuthenticationFailure(error);
                    Log.w(TAG, "Camera stream failed for slot " + (indexOf(tile) + 1)
                            + " (" + error.getErrorCodeName() + ")");
                    stop(tile);
                    setStatus(tile, authenticationFailed
                            ? "Authentication failed · check Tapo Camera Account"
                            : "Stream unavailable · check RTSP and network");
                }
            });
            player.setMediaSource(source);
            player.prepare();
            player.play();
        } catch (RuntimeException e) {
            setStatus(tile, "Could not start · check camera settings");
            Log.w(TAG, "Could not start camera stream for slot " + (indexOf(tile) + 1)
                    + " (" + e.getClass().getSimpleName() + ")");
            stop(tile);
        }
    }

    static boolean isAuthenticationFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getClass().getSimpleName().contains("Authentication")) return true;
            String message = cause.getMessage();
            if (message != null && message.contains("401")) return true;
        }
        return false;
    }

    private void setStatus(Tile tile, String message) {
        tile.status.setText(message);
        boolean live = "LIVE".equals(message);
        tile.status.setVisibility(live ? View.GONE : View.VISIBLE);
        setLive(tile.liveBadge, tile.liveBlink, live);
        int index = indexOf(tile);
        if (fullscreenTile == tile && fullscreenStatus != null) updateFullscreenStatus(message);
        if (fullscreenAll) updateFullscreenAllStatus(index, message);
    }

    private void updateFullscreenAllStatus(int index, String message) {
        TextView status = fullscreenStatuses[index];
        TextView badge = fullscreenBadges[index];
        ObjectAnimator animator = fullscreenBlinks[index];
        if (status != null) {
            status.setText(message);
            status.setVisibility("LIVE".equals(message) ? View.GONE : View.VISIBLE);
        }
        if (badge != null && animator != null) setLive(badge, animator, "LIVE".equals(message));
    }

    private void updateFullscreenStatus(String message) {
        if (fullscreenStatus == null) return;
        fullscreenStatus.setText(message);
        boolean live = "LIVE".equals(message);
        fullscreenStatus.setVisibility(live ? View.GONE : View.VISIBLE);
        if (fullscreenLiveBadge != null && fullscreenLiveBlink != null)
            setLive(fullscreenLiveBadge, fullscreenLiveBlink, live);
    }

    private TextView liveBadge() {
        TextView badge = text("● Live", 12, true);
        badge.setTextColor(0xFFFF3B30);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(9), 0, dp(9), 0);
        badge.setBackground(rounded(0xB8000000, 0x66FF3B30, 12));
        badge.setVisibility(View.GONE);
        return badge;
    }

    private static ObjectAnimator blink(View view) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.3f);
        animator.setDuration(650);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        return animator;
    }

    private void setLive(TextView badge, ObjectAnimator animator, boolean live) {
        if (live) {
            badge.setVisibility(View.VISIBLE);
            if (!animator.isStarted()) animator.start();
        } else {
            animator.cancel();
            badge.setAlpha(1f);
            badge.setVisibility(View.GONE);
        }
    }

    private void restoreTileSurface(Tile tile) {
        if (tile.player != null && tile.surfaceReady && tile.surface != null) {
            tile.player.setVideoSurface(tile.surface);
        }
        if (fullscreenSurface != null) {
            fullscreenSurface.release();
            fullscreenSurface = null;
        }
    }

    private void restoreTileSurface(int index) {
        Tile tile = tiles[index];
        Surface surface = fullscreenSurfaces[index];
        if (tile.player != null && tile.surfaceReady && tile.surface != null) {
            tile.player.setVideoSurface(tile.surface);
        } else if (tile.player != null) {
            tile.player.clearVideoSurface();
        }
        if (surface != null) {
            surface.release();
            fullscreenSurfaces[index] = null;
        }
    }

    private void stop(Tile tile) {
        ExoPlayer player = tile.player;
        tile.player = null;
        if (player != null) {
            player.clearVideoSurface();
            player.stop();
            player.release();
        }
    }

    private void loadSettings() {
        try {
            Config[] saved = loadSavedConfigs(context);
            for (int i = 0; i < tiles.length; i++) tiles[i].config = saved[i];
        } catch (IOException | GeneralSecurityException | JSONException | IllegalArgumentException e) {
            settingsError = "Could not decrypt saved camera settings";
            Log.e(TAG, "Could not load encrypted camera settings");
        }
    }

    static Config[] loadSavedConfigs(Context context) throws IOException, GeneralSecurityException, JSONException {
        Config[] configs = new Config[CAMERA_COUNT];
        android.util.AtomicFile file = new android.util.AtomicFile(
                new File(context.getNoBackupFilesDir(), "camera-streams.bin"));
        if (!file.getBaseFile().exists()) return configs;

        byte[] encrypted;
        try (java.io.InputStream input = file.openRead();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            encrypted = output.toByteArray();
        }
        byte[] packed = Base64.decode(encrypted, Base64.NO_WRAP);
        if (packed.length <= 12) throw new GeneralSecurityException("Invalid encrypted camera settings");
        byte[] iv = Arrays.copyOfRange(packed, 0, 12);
        byte[] ciphertext = Arrays.copyOfRange(packed, 12, packed.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), new GCMParameterSpec(128, iv));
        JSONArray values = new JSONArray(new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8));
        for (int i = 0; i < Math.min(values.length(), configs.length); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value == null || value.isNull("host")) continue;
            String name = value.optString("name", "").trim();
            if (name.isEmpty()) name = NAMES[i];
            configs[i] = new Config(name, value.getString("host"), value.getString("username"),
                    value.getString("password"), value.optBoolean("lowBandwidth", false));
        }
        return configs;
    }

    private void persistSettings() throws GeneralSecurityException, IOException, JSONException {
        JSONArray values = new JSONArray();
        for (Tile tile : tiles) {
            JSONObject value = new JSONObject();
            if (tile.config == null) value.put("host", JSONObject.NULL);
            else {
                value.put("host", tile.config.host);
                value.put("name", tile.config.name);
                value.put("username", tile.config.username);
                value.put("password", tile.config.password);
                value.put("lowBandwidth", tile.config.lowBandwidth);
            }
            values.put(value);
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(values.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(iv);
        output.write(ciphertext);
        byte[] packed = Base64.encode(output.toByteArray(), Base64.NO_WRAP);

        java.io.FileOutputStream stream = null;
        try {
            stream = settingsFile.startWrite();
            stream.write(packed);
            settingsFile.finishWrite(stream);
        } catch (IOException e) {
            if (stream != null) settingsFile.failWrite(stream);
            throw e;
        }
    }

    private static Key keystoreKey() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return store.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private TextView text(String value, int size, boolean bold) {
        TextView text = new TextView(getContext());
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        text.setTextColor(Color.WHITE);
        text.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        text.setIncludeFontPadding(false);
        return text;
    }

    private int dp(float value) { return Math.round(value * density); }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radiusDp));
        if (stroke != 0) shape.setStroke(dp(1), stroke);
        return shape;
    }
}
