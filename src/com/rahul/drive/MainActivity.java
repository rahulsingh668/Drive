package com.rahul.drive;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Native, offline home activity. No polling for phones, overlays, or foreground service. */
public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(238,241,243);
    private static final int MUTED = Color.rgb(169,177,181);
    private static final int ACCENT = Color.rgb(239,173,99);
    private static final int LINE = Color.rgb(52,58,62);
    private static final String[] SLOTS = {"Navigation", "Music", "Radio", "Phone", "Android Auto"};
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private FrameLayout surface;
    private TextView clock, date, mediaTitle, mediaArtist, mediaSource, elapsed, duration, volume, status;
    private ImageView artwork;
    private CarArt emptyArtwork;
    private Button play;
    private ProgressBar progress;
    private AudioManager audio;
    private MediaSessionManager sessions;
    private MediaController controller;
    private CarLetterMedia carLetterMedia;
    private boolean visible, night;
    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata data) { updateMedia(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { updateMedia(); }
        @Override public void onSessionDestroyed() { attachMedia(); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionListener = controllers -> attachMedia();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            updateClock(); updatePosition(); updateVolume();
            handler.postDelayed(this, controller != null && isPlaying() ? 1000 : 15000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("drive", MODE_PRIVATE);
        // Optional defaults for compatible NAM5260 installations; keep any user assignments.
        suggestApp("Navigation", "com.google.android.apps.maps/com.google.android.maps.MapsActivity");
        suggestApp("Music", "com.imotor.music/.ui.MainActivity");
        suggestApp("Radio", "com.imotor.fmam/.view.MainActivity");
        suggestApp("Phone", "com.imotor.contacts/.ui.MainActivity");
        suggestApp("Android Auto", "com.imotor.phoneconnect/.AndroidAuto");
        night = prefs.getBoolean("night", true);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        sessions = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        carLetterMedia = new CarLetterMedia(() -> { if (visible) updateMedia(); });
        // Retain this event receiver while our Home Activity is behind Android Auto.
        // No service, connection polling, or background rendering is involved.
        carLetterMedia.register(this);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        buildHome();
    }

    @Override protected void onResume() {
        super.onResume(); visible = true;
        // Only decorate our own window. Never launch ourselves in response to a phone connection.
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        MediaAccess.onConnectionChanged = this::refreshMediaAccess;
        refreshMediaAccess();
        handler.removeCallbacks(tick); tick.run();
    }

    @Override protected void onPause() {
        visible = false; handler.removeCallbacks(tick);
        MediaAccess.onConnectionChanged = null;
        sessions.removeOnActiveSessionsChangedListener(sessionListener);
        detachMedia();
        super.onPause();
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); }
    @Override protected void onDestroy() {
        if (carLetterMedia != null) unregisterReceiver(carLetterMedia);
        super.onDestroy();
    }
    @Override public void onBackPressed() { /* Home stays home. Dialogs handle their own Back. */ }

    private int panelColor() { return Color.parseColor(night ? "#1c2023" : "#363e45"); }
    private int backgroundColor() { return Color.parseColor(night ? "#111416" : "#252b30"); }

    private void buildHome() {
        surface = new FrameLayout(this);
        surface.setBackgroundColor(backgroundColor());
        ScreenFit fit = new ScreenFit(); fit.addView(surface); setContentView(fit);
        label("DRIVE", 40, 34, 220, 40, 25, INK, true);
        label("LAUNCHER", 189, 36, 180, 35, 13, MUTED, false);
        status = label("Ready for your drive", 845, 34, 395, 42, 17, MUTED, false);
        status.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        line(40, 98, 1200);

        label("Welcome.", 40, 128, 470, 42, 27, INK, false);
        clock = label("", 34, 175, 490, 118, 106, INK, false);
        clock.setFontFeatureSettings("tnum");
        date = label("", 42, 302, 480, 30, 18, MUTED, false);
        CarArt car = new CarArt(false);
        put(car, 42, 352, 112, 68);
        label("XUV 300", 170, 354, 280, 30, 21, Color.rgb(198,205,211), true);
        label("SILVER · YOUR CAR", 170, 388, 300, 24, 13, MUTED, false);
        button("Home", 40, 442, 180, 58, panelColor(), INK, () -> navigateSaved("home"));
        button("Work", 232, 442, 180, 58, panelColor(), INK, () -> navigateSaved("work"));

        View player = new View(this); player.setBackground(shape(panelColor(), 20)); put(player, 587, 128, 653, 372);
        mediaSource = label("MUSIC", 615, 151, 590, 30, 14, MUTED, false);
        emptyArtwork = new CarArt(true); put(emptyArtwork, 615, 201, 94, 94);
        artwork = new ImageView(this); artwork.setScaleType(ImageView.ScaleType.CENTER_CROP); put(artwork, 615, 201, 94, 94);
        mediaTitle = label("Ready when you are", 731, 204, 477, 44, 29, INK, false);
        mediaArtist = label("Open Music to start listening", 731, 254, 477, 34, 18, MUTED, false);
        mediaTitle.setOnClickListener(v -> { if (!hasMediaAccess()) showMediaAccessHelp(); });
        mediaArtist.setOnClickListener(v -> { if (!hasMediaAccess()) showMediaAccessHelp(); });
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(LINE));
        put(progress, 615, 321, 593, 5);
        elapsed = label("—:—", 615, 333, 130, 25, 14, MUTED, false);
        duration = label("—:—", 1078, 333, 130, 25, 14, MUTED, false); duration.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        button("|◀", 757, 386, 70, 70, panelColor(), INK, () -> transport("previous")).setContentDescription("Previous track");
        play = button("▶", 877, 386, 70, 70, ACCENT, backgroundColor(), () -> transport("play"));
        play.setContentDescription("Play music");
        button("▶|", 997, 386, 70, 70, panelColor(), INK, () -> transport("next")).setContentDescription("Next track");

        String[] names = {"Navigate", "Music", "Radio", "Phone", "Apps"};
        String[] icons = {"navigation", "music", "radio", "phone", "apps"};
        for (int i = 0; i < names.length; i++) {
            final int slot = i;
            int x = 40 + i * 243;
            Button b = button("\n" + names[i], x, 536, 228, 108, panelColor(), i == 0 ? ACCENT : INK,
                () -> { if(slot == 4) showApps(null); else launchSlot(SLOTS[slot]); });
            b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 21);
            b.setPadding(0, 25, 0, 0);
            b.setContentDescription(names[i]);
            if (i < 4) b.setOnLongClickListener(v -> { showApps(SLOTS[slot]); return true; });
            Symbol symbol = new Symbol(icons[i], i == 0 ? ACCENT : INK);
            symbol.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            put(symbol, x + 98, 550, 32, 32);
        }
        line(40, 664, 1200);
        button("Settings", 40, 670, 150, 48, backgroundColor(), MUTED, this::showSettings);
        button("Android Auto  ↗", 245, 670, 260, 48, backgroundColor(), ACCENT, () -> launchSlot("Android Auto"));
        button("−", 692, 670, 64, 48, backgroundColor(), INK, () -> changeVolume(-1)).setContentDescription("Lower media volume");
        volume = label("", 762, 676, 150, 36, 17, MUTED, false); volume.setGravity(Gravity.CENTER);
        button("+", 918, 670, 64, 48, backgroundColor(), INK, () -> changeVolume(1)).setContentDescription("Raise media volume");
        button(night ? "Night" : "Day", 1070, 670, 170, 48, backgroundColor(), MUTED, () -> {
            night = !night; prefs.edit().putBoolean("night", night).apply(); buildHome(); updateClock(); updateVolume(); updateMedia();
        });
        updateClock(); updateVolume(); updateMedia();
    }

    private void updateClock() {
        Date now = new Date();
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        date.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now));
    }

    private void updateVolume() {
        volume.setText("VOL  " + audio.getStreamVolume(AudioManager.STREAM_MUSIC));
    }

    private void changeVolume(int direction) {
        try { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0); }
        catch (SecurityException ignored) { toast("Use the stereo’s physical volume controls."); }
        updateVolume();
    }

    private void detachMedia() {
        if (controller != null) controller.unregisterCallback(mediaCallback);
        controller = null;
    }

    private void attachMedia() {
        if (!visible) return;
        detachMedia();
        try {
            List<MediaController> active = sessions.getActiveSessions(new ComponentName(this, MediaAccess.class));
            for (MediaController c : active) {
                if(controller == null) controller = c;
                if(c.getPlaybackState() != null && c.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) { controller = c; break; }
            }
            if(controller != null) controller.registerCallback(mediaCallback, handler);
        } catch (SecurityException ignored) { }
        updateMedia();
    }

    private boolean hasMediaAccess() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        ComponentName ours = new ComponentName(this, MediaAccess.class);
        for (String value : enabled.split(":")) if (ours.equals(ComponentName.unflattenFromString(value))) return true;
        return false;
    }

    private void refreshMediaAccess() {
        if (!visible) return;
        sessions.removeOnActiveSessionsChangedListener(sessionListener);
        attachMedia();
        try { sessions.addOnActiveSessionsChangedListener(sessionListener, new ComponentName(this, MediaAccess.class)); }
        catch (SecurityException ignored) { }
    }

    private void showMediaAccessHelp() {
        new AlertDialog.Builder(this).setTitle("Music controls")
            .setMessage("Enable DRIVE music controls in Android’s notification-access screen. Android grants access to notifications with this permission. DRIVE only uses active media sessions; it does not read or save notification messages.\n\nPhone music through Android Auto appears here only if the stereo’s projection app shares track information with Android. Otherwise, use the music controls inside Android Auto.")
            .setPositiveButton("Open access settings",(a,b)->openSystem(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
            .setNegativeButton("Cancel",null).show();
    }

    private boolean useCarLetterTrack() {
        if (carLetterMedia == null || !carLetterMedia.hasTrack()) return false;
        PlaybackState state = controller == null ? null : controller.getPlaybackState();
        MediaMetadata metadata = controller == null ? null : controller.getMetadata();
        boolean otherTrackPlaying = state != null && state.getState() == PlaybackState.STATE_PLAYING
            && metadata != null && !TextUtils.isEmpty(metadata.getText(MediaMetadata.METADATA_KEY_TITLE))
            && !controller.getPackageName().equals("com.carletter.car");
        return !otherTrackPlaying;
    }

    private boolean isPlaying() {
        if (useCarLetterTrack()) return carLetterMedia.playback == 1;
        PlaybackState state = controller == null ? null : controller.getPlaybackState();
        return state != null && state.getState() == PlaybackState.STATE_PLAYING;
    }

    private String text(CharSequence value, String fallback) { return TextUtils.isEmpty(value) ? fallback : value.toString(); }

    private void updateMedia() {
        if (mediaTitle == null) return;
        if (useCarLetterTrack()) {
            mediaTitle.setText(carLetterMedia.title);
            mediaArtist.setText(text(carLetterMedia.artist, "Phone music"));
            mediaSource.setText("ANDROID AUTO"); status.setText("Audio · Android Auto");
            artwork.setVisibility(View.INVISIBLE); emptyArtwork.setVisibility(View.VISIBLE);
            play.setText(isPlaying() ? "Ⅱ" : "▶");
            play.setContentDescription(isPlaying() ? "Pause music" : "Play music");
            updatePosition();
            if(visible) { handler.removeCallbacks(tick); handler.post(tick); }
            return;
        }
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        boolean access = hasMediaAccess();
        String title = m == null ? null : text(m.getText(MediaMetadata.METADATA_KEY_TITLE), text(m.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE), "Playing audio"));
        String artist = m == null ? null : text(m.getText(MediaMetadata.METADATA_KEY_ARTIST), text(m.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE), "Your music"));
        mediaTitle.setText(m == null ? (access ? "No track information" : "Enable music controls") : title);
        mediaArtist.setText(m == null ? (access ? "Phone playback may stay in Android Auto" : "Music access is off · Tap to enable") : artist);
        String source = "MUSIC";
        if(controller != null) {
            try { source = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(controller.getPackageName(), 0)).toString(); }
            catch (PackageManager.NameNotFoundException ignored) { source = "MUSIC"; }
        }
        mediaSource.setText(source.toUpperCase(Locale.getDefault()));
        status.setText(controller == null ? "Ready for your drive" : "Audio · " + source);
        play.setText(isPlaying() ? "Ⅱ" : "▶"); play.setContentDescription(isPlaying() ? "Pause music" : "Play music");
        android.graphics.Bitmap art = m == null ? null : m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if(art == null && m != null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        artwork.setImageBitmap(art); artwork.setVisibility(art == null ? View.INVISIBLE : View.VISIBLE);
        emptyArtwork.setVisibility(art == null ? View.VISIBLE : View.INVISIBLE);
        updatePosition();
        if(visible) { handler.removeCallbacks(tick); handler.post(tick); }
    }

    private void updatePosition() {
        if (useCarLetterTrack()) {
            elapsed.setText(time(carLetterMedia.positionMs)); duration.setText("—:—");
            progress.setProgress(0); return;
        }
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        PlaybackState state = controller == null ? null : controller.getPlaybackState();
        long length = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long pos = state == null ? 0 : state.getPosition();
        if(state != null && state.getState() == PlaybackState.STATE_PLAYING && state.getLastPositionUpdateTime() > 0)
            pos += (long)((SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime()) * state.getPlaybackSpeed());
        pos = Math.max(0, length > 0 ? Math.min(pos,length) : pos);
        progress.setProgress(length > 0 ? (int)(1000.0 * pos / length) : 0);
        elapsed.setText(state == null ? "—:—" : time(pos)); duration.setText(length > 0 ? time(length) : "—:—");
    }

    private String time(long ms) { return String.format(Locale.getDefault(), "%d:%02d", ms/60000, (ms/1000)%60); }

    private void transport(String action) {
        if (useCarLetterTrack()) {
            int key = action.equals("next") ? android.view.KeyEvent.KEYCODE_MEDIA_NEXT : action.equals("previous") ? android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS : android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
            audio.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,key));
            audio.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,key));
            return;
        }
        if(controller == null) {
            if(!hasMediaAccess()) showMediaAccessHelp();
            else toast("No controllable track. For phone playback, use Android Auto’s music controls.");
            return;
        }
        MediaController.TransportControls controls = controller.getTransportControls();
        switch(action) {
            case "previous": controls.skipToPrevious(); break;
            case "next": controls.skipToNext(); break;
            default: if(isPlaying()) controls.pause(); else controls.play();
        }
    }

    private List<ResolveInfo> appList() {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = new ArrayList<>(getPackageManager().queryIntentActivities(query, 0));
        java.util.Iterator<ResolveInfo> iterator = apps.iterator();
        while(iterator.hasNext()) if(iterator.next().activityInfo.packageName.equals(getPackageName())) iterator.remove();
        Collections.sort(apps, (a,b) -> a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
        return apps;
    }

    private void suggestApp(String slot, String flattened) {
        if (prefs.contains("app." + slot)) return;
        ComponentName component = ComponentName.unflattenFromString(flattened);
        if (component == null) return;
        try {
            android.content.pm.ActivityInfo info = getPackageManager().getActivityInfo(component, 0);
            if (info.enabled && info.exported && info.applicationInfo.enabled)
                prefs.edit().putString("app." + slot, component.flattenToString()).apply();
        } catch (PackageManager.NameNotFoundException ignored) { }
    }

    private void showApps(String bindSlot) {
        List<ResolveInfo> apps = appList();
        if(apps.isEmpty()) { toast("No launchable apps found."); return; }
        String[] labels = new String[apps.size()];
        for(int i=0;i<apps.size();i++) labels[i] = apps.get(i).loadLabel(getPackageManager()) + "\n" + apps.get(i).activityInfo.packageName;
        new AlertDialog.Builder(this).setTitle(bindSlot == null ? "Apps" : "Choose app for " + bindSlot)
            .setItems(labels, (d,which) -> {
                ResolveInfo r = apps.get(which);
                ComponentName component = new ComponentName(r.activityInfo.packageName,r.activityInfo.name);
                if(bindSlot != null) { prefs.edit().putString("app."+bindSlot, component.flattenToString()).apply(); toast(bindSlot + " shortcut saved"); }
                else launchComponent(component);
            }).setNegativeButton("Cancel", null).show();
    }

    private void launchSlot(String slot) {
        String saved = prefs.getString("app." + slot, null);
        if(saved != null) {
            ComponentName component = ComponentName.unflattenFromString(saved);
            if(component != null && launchComponent(component)) return;
            toast("That app is unavailable. Choose another.");
        }
        showApps(slot);
    }

    private boolean launchComponent(ComponentName component) {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) { return false; }
    }

    private void navigateSaved(String key) {
        String destination = prefs.getString("destination."+key, "");
        if(destination.isEmpty()) { editDestination(key); return; }
        Intent navigation = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination)));
        String saved = prefs.getString("app.Navigation", null);
        ComponentName component = saved == null ? null : ComponentName.unflattenFromString(saved);
        if(component != null) navigation.setPackage(component.getPackageName());
        try { startActivity(navigation); }
        catch (ActivityNotFoundException | SecurityException e) {
            toast("No compatible map app. Choose a navigation app in Settings.");
        }
    }

    private void editDestination(String key) {
        EditText field = new EditText(this); field.setSingleLine(); field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
        field.setText(prefs.getString("destination."+key, "")); field.setHint("Address or place name");
        new AlertDialog.Builder(this).setTitle("Set " + key + " destination").setView(field)
            .setPositiveButton("Save", (d,w) -> { prefs.edit().putString("destination."+key,field.getText().toString().trim()).apply(); })
            .setNegativeButton("Cancel",null).show();
    }

    private String bindingLabel(String slot) {
        String saved = prefs.getString("app."+slot,null);
        ComponentName c = saved == null ? null : ComponentName.unflattenFromString(saved);
        if(c != null) try { return getPackageManager().getActivityInfo(c,0).loadLabel(getPackageManager()).toString(); }
        catch (PackageManager.NameNotFoundException ignored) { }
        return "Choose app";
    }

    private void showSettings() {
        String[] items = new String[12];
        for(int i=0;i<SLOTS.length;i++) items[i] = SLOTS[i] + "  ·  " + bindingLabel(SLOTS[i]);
        items[5] = "Set Home destination"; items[6] = "Set Work destination";
        items[7] = "Enable music controls"; items[8] = "Set default Home launcher";
        items[9] = "Startup screen preview"; items[10] = "Android system settings"; items[11] = "About DRIVE / Android Auto";
        new AlertDialog.Builder(this).setTitle("DRIVE settings").setItems(items,(d,index) -> {
            if(index<5) showApps(SLOTS[index]);
            else if(index==5 || index==6) editDestination(index==5?"home":"work");
            else if(index==7) showMediaAccessHelp();
            else if(index==8) chooseHome();
            else if(index==9) previewStartup();
            else if(index==10) openSystem(new Intent(Settings.ACTION_SETTINGS));
            else new AlertDialog.Builder(this).setTitle("DRIVE 0.1.1")
                .setMessage("DRIVE Launcher · XUV 300\n1280 × 720 native layout · Android 6.0+\n\nAndroid Auto is provided by your stereo’s existing projection app. Keep its connection and auto-start settings enabled. DRIVE yields when another app opens and does not monitor connections or pull itself to the foreground. Select that projection app for the Android Auto shortcut.\n\nSwitching and return behavior must be tested on your stereo. The startup preview does not change the firmware boot logo.")
                .setPositiveButton("Done",null).show();
        }).setNegativeButton("Done",null).show();
    }

    private void chooseHome() {
        if(Build.VERSION.SDK_INT >= 29) {
            RoleManager role = (RoleManager) getSystemService(ROLE_SERVICE);
            if(role != null && role.isRoleAvailable(RoleManager.ROLE_HOME) && !role.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(role.createRequestRoleIntent(RoleManager.ROLE_HOME), 12); return;
            }
        }
        openSystem(new Intent(Settings.ACTION_HOME_SETTINGS));
    }

    private void openSystem(Intent intent) {
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException e) { toast("This settings page isn’t available on this stereo."); }
    }

    private void previewStartup() {
        FrameLayout stage = new FrameLayout(this); stage.setBackgroundColor(backgroundColor());
        TextView text = new TextView(this); text.setText("DRIVE\n\nWelcome."); text.setTextColor(INK);
        text.setTextSize(28); text.setGravity(Gravity.CENTER);
        stage.addView(text,new FrameLayout.LayoutParams(-1,260));
        new AlertDialog.Builder(this).setTitle("Startup concept · preview only").setView(stage).setPositiveButton("Close",null).show();
    }

    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }

    private TextView label(String value,int x,int y,int w,int h,int size,int color,boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextColor(color);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size); t.setGravity(Gravity.CENTER_VERTICAL);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        t.setSingleLine(); t.setEllipsize(TextUtils.TruncateAt.END); t.setIncludeFontPadding(false);
        put(t,x,y,w,h); return t;
    }

    private Button button(String title,int x,int y,int w,int h,int fill,int color,Runnable action) {
        Button b = new Button(this); b.setText(title); b.setTextColor(color); b.setAllCaps(false);
        b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,20); b.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(8,0,8,0); b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x30ffffff),shape(fill,16),null));
        b.setOnClickListener(v -> action.run()); put(b,x,y,w,h); return b;
    }

    private GradientDrawable shape(int color,int radius) { GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);return g; }
    private void line(int x,int y,int w) { View v=new View(this);v.setBackgroundColor(LINE);put(v,x,y,w,1); }
    private void put(View v,int x,int y,int w,int h) { FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;surface.addView(v,p); }

    /** Real 1280 x 720 coordinate space, letterboxed proportionally without a WebView. */
    private final class ScreenFit extends ViewGroup {
        ScreenFit() { super(MainActivity.this);setBackgroundColor(Color.BLACK); }
        @Override protected void onMeasure(int ws,int hs) {
            setMeasuredDimension(MeasureSpec.getSize(ws),MeasureSpec.getSize(hs));
            getChildAt(0).measure(MeasureSpec.makeMeasureSpec(1280,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(720,MeasureSpec.EXACTLY));
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
            int w=r-l,h=b-t; View child=getChildAt(0);
            child.layout((w-1280)/2,(h-720)/2,(w+1280)/2,(h+720)/2);
            float scale=Math.min(w/1280f,h/720f);child.setScaleX(scale);child.setScaleY(scale);
        }
    }

    /** Small original silver SUV illustration, not a manufacturer logo or photo. */
    private final class CarArt extends View {
        final Paint p=new Paint(3); final boolean music;
        CarArt(boolean isMusic) { super(MainActivity.this);music=isMusic;setContentDescription(music?"Music":"Silver XUV 300"); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);c.save();c.scale(getWidth()/112f,getHeight()/68f);
            if(music) {
                p.setColor(LINE);c.drawRoundRect(new RectF(0,0,112,68),9,9,p);
                p.setColor(ACCENT);p.setStrokeWidth(4);p.setStrokeCap(Paint.Cap.ROUND);
                for(int i=0;i<5;i++){int height=12+(2-Math.abs(2-i))*8;c.drawLine(34+i*11,34-height/2f,34+i*11,34+height/2f,p);}
            } else {
                p.setColor(0xff69747e);c.drawRoundRect(new RectF(15,53,35,66),4,4,p);c.drawRoundRect(new RectF(78,53,98,66),4,4,p);
                Path body=new Path();body.moveTo(12,54);body.lineTo(13,32);body.lineTo(23,12);body.quadTo(56,5,89,12);body.lineTo(99,32);body.lineTo(101,54);body.quadTo(56,62,12,54);p.setColor(0xffb8c2cb);c.drawPath(body,p);
                Path glass=new Path();glass.moveTo(24,17);glass.quadTo(56,12,88,17);glass.lineTo(95,33);glass.lineTo(18,33);glass.close();p.setColor(0xff26313a);c.drawPath(glass,p);
                p.setColor(0xffe8edf1);c.drawRoundRect(new RectF(17,38,37,44),3,3,p);c.drawRoundRect(new RectF(76,38,96,44),3,3,p);
                p.setColor(0xff3b454d);c.drawRoundRect(new RectF(40,38,73,48),3,3,p);p.setColor(0xffd8dfe5);p.setStrokeWidth(2);
                for(int i=0;i<5;i++) c.drawLine(44+i*6,40,44+i*6,46,p);
                p.setColor(0xff59656f);c.drawRoundRect(new RectF(27,51,86,56),2,2,p);
                p.setColor(0xffa5b0b9);c.drawRoundRect(new RectF(3,29,14,36),3,3,p);c.drawRoundRect(new RectF(98,29,109,36),3,3,p);
            }
            c.restore();
        }
    }

    private final class Symbol extends View {
        final String kind;final Paint p=new Paint(3);
        Symbol(String value,int color) { super(MainActivity.this);kind=value;p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND); }
        @Override protected void onDraw(Canvas c) {
            c.save();c.scale(getWidth()/32f,getHeight()/32f);
            Path path=new Path();
            if(kind.equals("navigation")){path.moveTo(27,5);path.lineTo(18,28);path.lineTo(13,18);path.lineTo(4,14);path.close();c.drawPath(path,p);}
            else if(kind.equals("music")){c.drawLine(13,23,13,8,p);c.drawLine(13,8,26,5,p);c.drawLine(26,5,26,20,p);c.drawOval(new RectF(5,21,13,28),p);c.drawOval(new RectF(18,18,26,25),p);}
            else if(kind.equals("radio")){c.drawRoundRect(new RectF(3,11,29,28),4,4,p);c.drawLine(7,11,24,4,p);c.drawCircle(11,20,5,p);c.drawLine(21,17,25,17,p);c.drawLine(21,23,25,23,p);}
            else if(kind.equals("phone")){path.moveTo(8,4);path.lineTo(4,9);path.quadTo(8,24,23,28);path.lineTo(28,23);path.lineTo(21,18);path.lineTo(18,21);path.quadTo(11,18,10,12);path.lineTo(13,9);path.close();c.drawPath(path,p);}
            else {for(int i=0;i<4;i++){int x=4+(i%2)*14,y=4+(i/2)*14;c.drawRoundRect(new RectF(x,y,x+10,y+10),2,2,p);}}
            c.restore();
        }
    }
}
