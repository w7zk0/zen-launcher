package app.zen.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Prefs {
    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences("zen", Context.MODE_PRIVATE);
    }

    public boolean seeded() {
        return sp.getBoolean("seeded", false);
    }

    public void setSeeded() {
        sp.edit().putBoolean("seeded", true).apply();
    }

    public boolean openedDrawer() {
        return sp.getBoolean("openedDrawer", false);
    }

    public void setOpenedDrawer() {
        sp.edit().putBoolean("openedDrawer", true).apply();
    }

    public String theme() {
        return sp.getString("theme", "night");
    }

    public void theme(String value) {
        sp.edit().putString("theme", value).apply();
    }

    public String font() {
        return "workbench".equals(sp.getString("font", "zen")) ? "workbench" : "zen";
    }

    public void font(String value) {
        sp.edit().putString("font", "workbench".equals(value) ? "workbench" : "zen").apply();
    }

    public String align() {
        return sp.getString("align", "center");
    }

    public void align(String value) {
        sp.edit().putString("align", value).apply();
    }

    public String textSize() {
        return sp.getString("text", "m");
    }

    public void textSize(String value) {
        sp.edit().putString("text", value).apply();
    }

    public boolean showClock() {
        return sp.getBoolean("clock", true);
    }

    public void showClock(boolean value) {
        sp.edit().putBoolean("clock", value).apply();
    }

    public boolean showDate() {
        return sp.getBoolean("date", true);
    }

    public void showDate(boolean value) {
        sp.edit().putBoolean("date", value).apply();
    }

    public boolean hour24() {
        return sp.getBoolean("h24", false);
    }

    public void hour24(boolean value) {
        sp.edit().putBoolean("h24", value).apply();
    }

    public boolean statusBar() {
        return sp.getBoolean("status", true);
    }

    public void statusBar(boolean value) {
        sp.edit().putBoolean("status", value).apply();
    }

    public String background() {
        return sp.getString("bg", "daily");
    }

    public void background(String value) {
        sp.edit().putString("bg", value).apply();
    }

    public boolean doubleTapLock() {
        return sp.getBoolean("dtlock", false);
    }

    public void doubleTapLock(boolean value) {
        sp.edit().putBoolean("dtlock", value).apply();
    }

    public String swipeLeft() {
        return sp.getString("swipeL", "");
    }

    public void swipeLeft(String id) {
        sp.edit().putString("swipeL", id == null ? "" : id).apply();
    }

    public String swipeRight() {
        return sp.getString("swipeR", "");
    }

    public void swipeRight(String id) {
        sp.edit().putString("swipeR", id == null ? "" : id).apply();
    }

    public List<String> home() {
        return readList("home");
    }

    public void home(List<String> ids) {
        writeList("home", ids);
    }

    public Set<String> hidden() {
        return new HashSet<>(readList("hidden"));
    }

    public void hidden(Set<String> packages) {
        writeList("hidden", new ArrayList<>(packages));
    }

    public Map<String, String> renames() {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            JSONObject object = new JSONObject(sp.getString("renames", "{}"));
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(key, object.optString(key));
            }
        } catch (JSONException ignored) {
            // keep the empty map
        }
        return map;
    }

    public void rename(String packageName, String name) {
        Map<String, String> map = renames();
        if (name == null || name.trim().isEmpty()) map.remove(packageName);
        else map.put(packageName, name.trim());
        JSONObject object = new JSONObject();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            try {
                object.put(entry.getKey(), entry.getValue());
            } catch (JSONException ignored) {
                // skip a single bad key
            }
        }
        sp.edit().putString("renames", object.toString()).apply();
    }

    private List<String> readList(String key) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(sp.getString(key, "[]"));
            for (int i = 0; i < array.length(); i++) out.add(array.getString(i));
        } catch (JSONException ignored) {
            // empty
        }
        return out;
    }

    private void writeList(String key, List<String> list) {
        JSONArray array = new JSONArray();
        for (String item : list) array.put(item);
        sp.edit().putString(key, array.toString()).apply();
    }
}
