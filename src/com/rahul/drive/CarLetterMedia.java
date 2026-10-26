package com.rahul.drive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.Iterator;

/** Event-driven adapter for the broadcasts in the installed CarLetter 7.5.8. */
final class CarLetterMedia extends BroadcastReceiver {
    static final String ID3 = "action.carletter.media.id3";
    static final String STATUS = "action.carletter.media.play.status";
    String title = "", artist = "";
    long updatedAt, positionMs;
    int playback = -1;
    private final Runnable changed;

    CarLetterMedia(Runnable callback) { changed = callback; }

    void register(Context context) {
        IntentFilter filter = new IntentFilter(); filter.addAction(ID3); filter.addAction(STATUS);
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(this, filter, Context.RECEIVER_EXPORTED);
        else context.registerReceiver(this, filter);
    }

    boolean hasTrack() {
        return !title.isEmpty() && SystemClock.elapsedRealtime() - updatedAt < 10 * 60 * 1000L;
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (ID3.equals(intent.getAction())) {
            String raw = intent.getStringExtra("id3info");
            if (raw == null || raw.length() > 32768) return;
            try {
                JSONObject data = new JSONObject(raw);
                String name = findText(data, new String[]{"title", "name", "songname", "songtitle", "trackname", "musictitle", "musicname"}, 0);
                String performer = findText(data, new String[]{"artist", "artistname", "singer", "author"}, 0);
                if (name.isEmpty()) { title = ""; artist = ""; }
                else { title = limit(name); artist = limit(performer); }
                positionMs = 0; updatedAt = SystemClock.elapsedRealtime();
            } catch (org.json.JSONException | IllegalArgumentException ignored) { return; }
        } else if (STATUS.equals(intent.getAction())) {
            playback = intent.getIntExtra("playStatus", -1);
            positionMs = Math.max(0, intent.getIntExtra("playTime", 0)) * 1000L;
            if (!title.isEmpty()) updatedAt = SystemClock.elapsedRealtime();
        } else return;
        changed.run();
    }

    private String limit(String text) { return text.length() > 512 ? text.substring(0,512) : text; }

    private String findText(JSONObject object, String[] candidates, int depth) {
        if (depth > 3) return "";
        for (String wanted : candidates) {
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.replace("_", "").equalsIgnoreCase(wanted)) {
                    Object value = object.opt(key);
                    if (value instanceof String && !((String)value).trim().isEmpty()) return ((String)value).trim();
                }
            }
        }
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            Object value = object.opt(keys.next());
            if (value instanceof JSONObject) {
                String nested = findText((JSONObject)value, candidates, depth+1);
                if (!nested.isEmpty()) return nested;
            }
        }
        return "";
    }
}
