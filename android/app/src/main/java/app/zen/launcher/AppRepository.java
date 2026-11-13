package app.zen.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AppRepository {
    private final PackageManager pm;
    private final Prefs prefs;
    private final String self;

    public AppRepository(Context context, Prefs prefs) {
        this.pm = context.getPackageManager();
        this.prefs = prefs;
        this.self = context.getPackageName();
    }

    public List<AppEntry> all(boolean includeHidden) {
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(probe, 0);
        Map<String, String> renames = prefs.renames();
        Set<String> hidden = prefs.hidden();
        List<AppEntry> out = new ArrayList<>();
        for (ResolveInfo info : infos) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg == null || pkg.equals(self) || pkg.startsWith(self + ".")) continue;
            if (!includeHidden && hidden.contains(pkg)) continue;
            CharSequence loaded = info.loadLabel(pm);
            String raw = loaded == null ? "" : loaded.toString().trim();
            if (raw.isEmpty()) raw = pkg;
            String renamed = renames.get(pkg);
            String label = renamed == null || renamed.trim().isEmpty() ? raw : renamed;
            out.add(new AppEntry(pkg, info.activityInfo.name, label));
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    public List<AppEntry> home() {
        Map<String, AppEntry> byId = new HashMap<>();
        for (AppEntry entry : all(false)) byId.put(entry.id(), entry);
        List<AppEntry> out = new ArrayList<>();
        for (String id : prefs.home()) {
            AppEntry entry = byId.get(id);
            if (entry != null) out.add(entry);
        }
        return out;
    }

    public AppEntry find(String id, boolean includeHidden) {
        if (id == null || id.isEmpty()) return null;
        for (AppEntry entry : all(includeHidden)) {
            if (entry.id().equals(id)) return entry;
        }
        return null;
    }

    public void seedIfNeeded() {
        if (prefs.seeded()) return;
        List<AppEntry> apps = all(true);
        String[] wants = {"phone", "telephone", "dialer", "messages", "messaging", "camera", "maps", "clock", "calendar"};
        List<String> picked = new ArrayList<>();
        for (String want : wants) {
            if (picked.size() >= 6) break;
            AppEntry best = null;
            int bestScore = 0;
            for (AppEntry entry : apps) {
                if (containsId(picked, entry)) continue;
                int score = score(entry.label, want);
                if (score > bestScore) {
                    bestScore = score;
                    best = entry;
                }
            }
            if (best != null) picked.add(best.id());
        }
        if (picked.isEmpty()) {
            for (int i = 0; i < apps.size() && picked.size() < 5; i++) picked.add(apps.get(i).id());
        }
        prefs.home(picked);
        for (AppEntry entry : apps) {
            String label = entry.label.toLowerCase(Locale.US);
            if (prefs.swipeRight().isEmpty() && label.contains("camera")) prefs.swipeRight(entry.id());
            if (prefs.swipeLeft().isEmpty() && (label.contains("music") || label.contains("spotify"))) {
                prefs.swipeLeft(entry.id());
            }
        }
        prefs.setSeeded();
    }

    private static boolean containsId(List<String> ids, AppEntry entry) {
        for (String id : ids) {
            if (id.startsWith(entry.packageName + "/")) return true;
        }
        return false;
    }

    private static int score(String label, String want) {
        String value = label.toLowerCase(Locale.US);
        if (value.equals(want)) return 3;
        if (value.startsWith(want)) return 2;
        if (value.contains(want)) return 1;
        return 0;
    }

    public Intent launchIntent(AppEntry entry) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(entry.packageName, entry.activityName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
