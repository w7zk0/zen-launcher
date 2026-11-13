package app.zen.launcher;

import androidx.appcompat.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HomeActivity extends AppCompatActivity {
    private static final String[] BACKGROUNDS = {"daily", "mist", "shore", "night", "wallpaper"};
    private static final String[] SIZES = {"s", "m", "l"};
    private static final String[] ALIGNS = {"left", "center", "right"};
    private static final String[] FONTS = {"zen", "workbench"};

    private Prefs prefs;
    private AppRepository repo;
    private InterceptLayout root;
    private ImageView zenBg;
    private View scrim;
    private LinearLayout homeColumn;
    private LinearLayout homeApps;
    private TextView clock;
    private TextView dateView;
    private TextView hint;
    private LinearLayout drawer;
    private EditText search;
    private View searchRule;
    private LinearLayout allApps;
    private TextView drawerHelp;
    private ScrollView settings;
    private LinearLayout settingsBody;
    private Typeface clockFace;
    private Typeface appFace;
    private Typeface newsreaderFace;
    private Typeface outfitFace;
    private Typeface workbenchFace;
    private boolean suppressAuto;
    private WindowInsetsCompat lastInsets;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            bindClock();
            handler.postDelayed(this, 15000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        repo = new AppRepository(this, prefs);
        repo.seedIfNeeded();
        newsreaderFace = ResourcesCompat.getFont(this, R.font.newsreader_light);
        outfitFace = ResourcesCompat.getFont(this, R.font.outfit_regular);
        workbenchFace = ResourcesCompat.getFont(this, R.font.workbench);
        if (newsreaderFace == null) newsreaderFace = Typeface.SERIF;
        if (outfitFace == null) outfitFace = Typeface.SANS_SERIF;
        if (workbenchFace == null) workbenchFace = Typeface.MONOSPACE;
        clockFace = newsreaderFace;
        appFace = outfitFace;

        setContentView(R.layout.activity_home);
        root = findViewById(R.id.root);
        zenBg = findViewById(R.id.zen_bg);
        scrim = findViewById(R.id.scrim);
        homeColumn = findViewById(R.id.home_column);
        homeApps = findViewById(R.id.home_apps);
        clock = findViewById(R.id.clock);
        dateView = findViewById(R.id.date);
        hint = findViewById(R.id.hint);
        drawer = findViewById(R.id.drawer);
        search = findViewById(R.id.search);
        searchRule = findViewById(R.id.search_rule);
        allApps = findViewById(R.id.all_apps);
        drawerHelp = findViewById(R.id.drawer_help);
        settings = findViewById(R.id.settings);
        settingsBody = findViewById(R.id.settings_body);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            lastInsets = insets;
            applyChrome();
            return insets;
        });

        GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                View hit = findHomeApp(e);
                if (hit != null && hit.getTag() instanceof AppEntry) {
                    launch((AppEntry) hit.getTag());
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (findHomeApp(e) != null) return true;
                if (prefs.doubleTapLock()) lockScreen();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                root.markLongPress();
                root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                View hit = findHomeApp(e);
                if (hit != null && hit.getTag() instanceof AppEntry) showMenu((AppEntry) hit.getTag());
                else openSettings();
            }
        });
        root.detector = detector;
        root.setSwipeListener(direction -> {
            if (direction == InterceptLayout.SWIPE_UP) openDrawer();
            else if (direction == InterceptLayout.SWIPE_DOWN) expandNotifications();
            else if (direction == InterceptLayout.SWIPE_LEFT) launchId(prefs.swipeLeft());
            else if (direction == InterceptLayout.SWIPE_RIGHT) launchId(prefs.swipeRight());
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                filterDrawer(s == null ? "" : s.toString());
            }
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                openFirstMatch();
                return true;
            }
            return false;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (settings.getVisibility() == View.VISIBLE) closeSettings();
                else if (drawer.getVisibility() == View.VISIBLE) closeDrawer();
                else moveTaskToBack(true);
            }
        });

        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) applyChrome();
        });
        paint();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        closeSettings();
        closeDrawer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        paint();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private void paint() {
        applyType();
        boolean night = night();
        int text = textColor();
        int dim = dimColor();
        clock.setTextColor(text);
        clock.setTypeface(clockFace);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, clockSp());
        clock.setGravity(gravity() | Gravity.CENTER_VERTICAL);
        dateView.setTextColor(dim);
        dateView.setTypeface(appFace);
        dateView.setGravity(gravity());
        hint.setTextColor(dim);
        hint.setGravity(gravity());
        clock.setShadowLayer(night ? 18f : 0f, 0f, 2f, 0x88000000);
        dateView.setShadowLayer(night ? 12f : 0f, 0f, 1f, 0x66000000);

        bindBackground();
        bindClock();
        dateView.setVisibility(prefs.showDate() ? View.VISIBLE : View.GONE);
        clock.setVisibility(prefs.showClock() ? View.VISIBLE : View.GONE);
        boolean showHint = !prefs.openedDrawer() || repo.home().isEmpty();
        hint.setVisibility(showHint ? View.VISIBLE : View.GONE);

        drawer.setBackgroundColor(sheetColor());
        settings.setBackgroundColor(sheetColor());
        search.setTextColor(text);
        search.setHintTextColor(dim);
        searchRule.setBackgroundColor(night ? 0x33F4F0E8 : 0x331C1915);
        drawerHelp.setTextColor(dim);
        applyStatusBar();
        bindHomeApps();
        if (settings.getVisibility() == View.VISIBLE) renderSettings();
        if (drawer.getVisibility() == View.VISIBLE) filterDrawer(search.getText().toString());
    }

    private void bindBackground() {
        String mode = prefs.background();
        if ("wallpaper".equals(mode)) {
            zenBg.setVisibility(View.GONE);
            getWindow().setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            zenBg.setVisibility(View.VISIBLE);
            zenBg.setImageResource(wallpaperRes(mode));
        }
        int[] colors = night()
                ? new int[]{0x33000000, 0x66000000, 0xB3000000}
                : new int[]{0x99F3EFE6, 0xD0F3EFE6, 0xF2F3EFE6};
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors));
    }

    private int wallpaperRes(String mode) {
        if ("mist".equals(mode)) return R.drawable.wall_mist;
        if ("shore".equals(mode)) return R.drawable.wall_shore;
        if ("night".equals(mode)) return R.drawable.wall_night;
        int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        int[] plates = {R.drawable.wall_mist, R.drawable.wall_shore, R.drawable.wall_night};
        return plates[day % plates.length];
    }

    private void bindClock() {
        Calendar calendar = Calendar.getInstance();
        int minute = calendar.get(Calendar.MINUTE);
        dateView.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date()));
        if (prefs.hour24()) {
            clock.setText(String.format(Locale.getDefault(), "%02d:%02d",
                    calendar.get(Calendar.HOUR_OF_DAY), minute));
            return;
        }
        int hour = calendar.get(Calendar.HOUR);
        if (hour == 0) hour = 12;
        String ap = calendar.get(Calendar.AM_PM) == Calendar.AM ? "am" : "pm";
        String face = hour + ":" + String.format(Locale.US, "%02d", minute) + " " + ap;
        SpannableString span = new SpannableString(face);
        float suffix = workbench() ? 0.55f : 0.32f;
        span.setSpan(new RelativeSizeSpan(suffix), face.length() - 2, face.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        clock.setText(span);
    }

    private void bindHomeApps() {
        homeApps.removeAllViews();
        for (AppEntry entry : repo.home()) homeApps.addView(homeLabel(entry));
    }

    private TextView homeLabel(AppEntry entry) {
        TextView view = new TextView(this);
        view.setText(entry.label);
        view.setTag(entry);
        view.setTextColor(textColor());
        view.setTypeface(appFace);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, appSp());
        view.setGravity(gravity());
        view.setPadding(0, dp(10), 0, dp(10));
        view.setShadowLayer(night() ? 12f : 0f, 0f, 1f, 0x88000000);
        view.setContentDescription("Open " + entry.label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(params);
        return view;
    }

    private View findHomeApp(MotionEvent event) {
        int[] loc = new int[2];
        for (int i = 0; i < homeApps.getChildCount(); i++) {
            View child = homeApps.getChildAt(i);
            child.getLocationOnScreen(loc);
            float x = event.getRawX();
            float y = event.getRawY();
            if (x >= loc[0] && x <= loc[0] + child.getWidth()
                    && y >= loc[1] - dp(4) && y <= loc[1] + child.getHeight() + dp(4)) {
                return child;
            }
        }
        return null;
    }

    private void openDrawer() {
        prefs.setOpenedDrawer();
        hint.setVisibility(View.GONE);
        closeSettings();
        suppressAuto = true;
        search.setText("");
        suppressAuto = false;
        drawer.setVisibility(View.VISIBLE);
        root.setGesturesEnabled(false);
        filterDrawer("");
        search.requestFocus();
        search.post(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void closeDrawer() {
        if (drawer.getVisibility() != View.VISIBLE) {
            root.setGesturesEnabled(settings.getVisibility() != View.VISIBLE);
            return;
        }
        suppressAuto = true;
        search.setText("");
        suppressAuto = false;
        drawer.setVisibility(View.GONE);
        hideKeyboard();
        root.setGesturesEnabled(true);
        bindHomeApps();
    }

    private void openSettings() {
        closeDrawer();
        settings.setVisibility(View.VISIBLE);
        root.setGesturesEnabled(false);
        renderSettings();
        settings.scrollTo(0, 0);
    }

    private void closeSettings() {
        settings.setVisibility(View.GONE);
        root.setGesturesEnabled(drawer.getVisibility() != View.VISIBLE);
    }

    private void filterDrawer(String raw) {
        boolean noAuto = raw.startsWith(" ");
        String query = raw.trim().toLowerCase(Locale.ROOT);
        allApps.removeAllViews();
        List<AppEntry> matched = new ArrayList<>();
        for (AppEntry entry : repo.all(false)) {
            if (query.isEmpty() || entry.label.toLowerCase(Locale.ROOT).contains(query)) matched.add(entry);
        }
        if (matched.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nothing under that name");
            empty.setTextColor(dimColor());
            empty.setTypeface(appFace);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            empty.setPadding(0, dp(18), 0, dp(8));
            allApps.addView(empty);
        }
        for (AppEntry entry : matched) allApps.addView(drawerRow(entry));
        drawerHelp.setVisibility(query.isEmpty() ? View.VISIBLE : View.GONE);
        if (!suppressAuto && drawer.getVisibility() == View.VISIBLE && !noAuto
                && !query.isEmpty() && matched.size() == 1) {
            launch(matched.get(0));
        }
    }

    private void openFirstMatch() {
        String raw = search.getText() == null ? "" : search.getText().toString();
        String query = raw.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return;
        for (AppEntry entry : repo.all(false)) {
            if (entry.label.toLowerCase(Locale.ROOT).contains(query)) {
                launch(entry);
                return;
            }
        }
    }

    private TextView drawerRow(AppEntry entry) {
        TextView view = new TextView(this);
        view.setText(entry.label);
        view.setTextColor(textColor());
        view.setTypeface(appFace);
        view.setIncludeFontPadding(workbench());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, appSp());
        view.setGravity(gravity());
        view.setPadding(0, dp(12), 0, dp(12));
        view.setOnClickListener(v -> launch(entry));
        view.setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showMenu(entry);
            return true;
        });
        return view;
    }

    private void launch(AppEntry entry) {
        closeDrawer();
        closeSettings();
        try {
            startActivity(repo.launchIntent(entry));
        } catch (Exception ex) {
            Toast.makeText(this, "Can’t open " + entry.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void launchId(String id) {
        AppEntry entry = repo.find(id, false);
        if (entry != null) launch(entry);
    }

    private void showMenu(AppEntry entry) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        boolean onHome = prefs.home().contains(entry.id());
        if (onHome) {
            labels.add("Remove from home");
            actions.add(() -> {
                List<String> ids = new ArrayList<>(prefs.home());
                ids.remove(entry.id());
                prefs.home(ids);
                paint();
            });
            labels.add("Move up");
            actions.add(() -> moveHome(entry, -1));
            labels.add("Move down");
            actions.add(() -> moveHome(entry, 1));
        } else {
            labels.add("Add to home");
            actions.add(() -> {
                List<String> ids = new ArrayList<>(prefs.home());
                if (!ids.contains(entry.id())) ids.add(entry.id());
                prefs.home(ids);
                paint();
            });
        }
        labels.add("Rename");
        actions.add(() -> rename(entry));
        labels.add(prefs.hidden().contains(entry.packageName) ? "Unhide" : "Hide");
        actions.add(() -> {
            Set<String> hidden = prefs.hidden();
            if (!hidden.add(entry.packageName)) hidden.remove(entry.packageName);
            prefs.hidden(hidden);
            paint();
        });
        labels.add("App info");
        actions.add(() -> appInfo(entry));
        labels.add("Uninstall");
        actions.add(() -> uninstall(entry));
        new AlertDialog.Builder(this, R.style.ZenDialog)
                .setTitle(entry.label)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .show();
    }

    private void moveHome(AppEntry entry, int delta) {
        List<String> ids = new ArrayList<>(prefs.home());
        int index = ids.indexOf(entry.id());
        int next = index + delta;
        if (index < 0 || next < 0 || next >= ids.size()) return;
        Collections.swap(ids, index, next);
        prefs.home(ids);
        paint();
    }

    private void rename(AppEntry entry) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(entry.label);
        input.setSelection(input.getText().length());
        input.setTextColor(0xFF1C1915);
        input.setPadding(dp(20), dp(16), dp(20), dp(8));
        new AlertDialog.Builder(this, R.style.ZenDialog)
                .setTitle("Rename")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.rename(entry.packageName, input.getText().toString());
                    paint();
                })
                .setNeutralButton("Reset", (d, w) -> {
                    prefs.rename(entry.packageName, "");
                    paint();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void appInfo(AppEntry entry) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + entry.packageName));
        try {
            startActivity(intent);
        } catch (Exception ex) {
            Toast.makeText(this, "App info isn’t available", Toast.LENGTH_SHORT).show();
        }
    }

    private void uninstall(AppEntry entry) {
        Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + entry.packageName));
        try {
            startActivity(intent);
        } catch (Exception ex) {
            Toast.makeText(this, "Can’t uninstall " + entry.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void renderSettings() {
        settingsBody.removeAllViews();
        addClose();
        addSection("Appearance");
        addChoice("Theme", night() ? "Night" : "Day", () -> {
            prefs.theme(night() ? "day" : "night");
            paint();
        });
        addChoice("Font", fontLabel(prefs.font()), () -> {
            prefs.font(next(FONTS, prefs.font()));
            paint();
        });
        addChoice("Background", backgroundLabel(prefs.background()), () -> {
            prefs.background(next(BACKGROUNDS, prefs.background()));
            paint();
        });
        addChoice("Text", sizeLabel(prefs.textSize()), () -> {
            prefs.textSize(next(SIZES, prefs.textSize()));
            paint();
        });
        addChoice("Align", alignLabel(prefs.align()), () -> {
            prefs.align(next(ALIGNS, prefs.align()));
            paint();
        });
        addChoice("Clock", prefs.showClock() ? "Shown" : "Hidden", () -> {
            prefs.showClock(!prefs.showClock());
            paint();
        });
        addChoice("Date", prefs.showDate() ? "Shown" : "Hidden", () -> {
            prefs.showDate(!prefs.showDate());
            paint();
        });
        addChoice("Time", prefs.hour24() ? "24-hour" : "12-hour", () -> {
            prefs.hour24(!prefs.hour24());
            paint();
        });
        addChoice("Status bar", prefs.statusBar() ? "Shown" : "Hidden", () -> {
            prefs.statusBar(!prefs.statusBar());
            paint();
        });

        addSection("Gestures");
        addChoice("Swipe left", swipeLabel(prefs.swipeLeft()), () -> pickApp("Swipe left", true, chosen -> {
            prefs.swipeLeft(chosen == null ? "" : chosen.id());
            paint();
        }));
        addChoice("Swipe right", swipeLabel(prefs.swipeRight()), () -> pickApp("Swipe right", true, chosen -> {
            prefs.swipeRight(chosen == null ? "" : chosen.id());
            paint();
        }));
        addChoice("Double-tap", prefs.doubleTapLock() ? "Lock" : "Off", () -> {
            boolean next = !prefs.doubleTapLock();
            prefs.doubleTapLock(next);
            paint();
            if (next) ensureAdmin();
        });

        addSection("On your home");
        List<AppEntry> home = repo.home();
        if (home.isEmpty()) addNote("Swipe up, then long-press an app to keep it here.");
        for (AppEntry entry : home) {
            addChoice(entry.label, "Edit", () -> showMenu(entry));
        }
        addChoice("Add an app", "", () -> pickApp("Add to home", false, chosen -> {
            if (chosen == null) return;
            List<String> ids = new ArrayList<>(prefs.home());
            if (!ids.contains(chosen.id())) ids.add(chosen.id());
            prefs.home(ids);
            paint();
        }));

        List<AppEntry> hidden = new ArrayList<>();
        Set<String> hiddenSet = prefs.hidden();
        for (AppEntry entry : repo.all(true)) {
            if (hiddenSet.contains(entry.packageName)) hidden.add(entry);
        }
        if (!hidden.isEmpty()) {
            addSection("Hidden");
            for (AppEntry entry : hidden) {
                addChoice(entry.label, "Restore", () -> {
                    Set<String> set = prefs.hidden();
                    set.remove(entry.packageName);
                    prefs.hidden(set);
                    paint();
                });
            }
        }

        addSection("Zen");
        addChoice("Set as home screen", "", this::askToBeHome);
        addChoice("About", "", this::about);
    }

    private void addClose() {
        TextView close = new TextView(this);
        close.setText("Close");
        close.setTextColor(textColor());
        close.setTypeface(appFace);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        close.setPadding(0, dp(6), 0, dp(18));
        close.setOnClickListener(v -> closeSettings());
        settingsBody.addView(close);
    }

    private void addSection(String title) {
        TextView view = new TextView(this);
        view.setText(title.toUpperCase(Locale.getDefault()));
        view.setTextColor(dimColor());
        view.setTypeface(appFace);
        view.setLetterSpacing(workbench() ? 0.06f : 0.16f);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        view.setPadding(0, dp(22), 0, dp(4));
        settingsBody.addView(view);
    }

    private void addNote(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(dimColor());
        view.setTypeface(appFace);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        view.setPadding(0, dp(10), 0, dp(10));
        settingsBody.addView(view);
    }

    private void addChoice(String title, String value, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(13), 0, dp(13));
        row.setOnClickListener(v -> action.run());
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(textColor());
        label.setTypeface(appFace);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label);
        if (value != null && !value.isEmpty()) {
            TextView meta = new TextView(this);
            meta.setText(value);
            meta.setTextColor(dimColor());
            meta.setTypeface(appFace);
            meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            row.addView(meta);
        }
        settingsBody.addView(row);
    }

    private void pickApp(String title, boolean includeNone, java.util.function.Consumer<AppEntry> onPick) {
        List<AppEntry> apps = repo.all(false);
        int extra = includeNone ? 1 : 0;
        String[] names = new String[apps.size() + extra];
        if (includeNone) names[0] = "None";
        for (int i = 0; i < apps.size(); i++) names[i + extra] = apps.get(i).label;
        new AlertDialog.Builder(this, R.style.ZenDialog)
                .setTitle(title)
                .setItems(names, (dialog, which) -> {
                    if (includeNone && which == 0) onPick.accept(null);
                    else onPick.accept(apps.get(which - extra));
                })
                .show();
    }

    private void askToBeHome() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception ex) {
            Toast.makeText(this, "Press Home and choose Zen.", Toast.LENGTH_LONG).show();
        }
    }

    private void about() {
        new AlertDialog.Builder(this, R.style.ZenDialog)
                .setTitle("Zen")
                .setMessage("A quiet home screen.\n\nNo icons, no badges, no account. Apps stay as words on the wallpaper.\n\nZen does not use the network. Double-tap lock, if you turn it on, only locks the screen.")
                .setPositiveButton("Close", null)
                .show();
    }

    private void lockScreen() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, ZenAdmin.class);
        if (dpm != null && dpm.isAdminActive(admin)) {
            dpm.lockNow();
            return;
        }
        ensureAdmin();
    }

    private void ensureAdmin() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, ZenAdmin.class);
        if (dpm != null && dpm.isAdminActive(admin)) return;
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Zen uses this only to lock the screen when you double-tap. It cannot wipe or change the device.");
        try {
            startActivity(intent);
        } catch (Exception ex) {
            Toast.makeText(this, "Couldn’t ask to lock the screen", Toast.LENGTH_SHORT).show();
        }
    }

    private void expandNotifications() {
        try {
            Object service = getSystemService("statusbar");
            Class<?> type = Class.forName("android.app.StatusBarManager");
            Method method = type.getMethod("expandNotificationsPanel");
            method.invoke(service);
        } catch (Throwable first) {
            try {
                Object service = getSystemService("statusbar");
                Method method = service.getClass().getMethod("expandNotificationsPanel");
                method.setAccessible(true);
                method.invoke(service);
            } catch (Throwable ignored) {
                Toast.makeText(this, "Pull down from the top edge", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    private void applyStatusBar() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), root);
        if (controller == null) return;
        controller.setAppearanceLightStatusBars(!night());
        controller.setAppearanceLightNavigationBars(!night());
        if (prefs.statusBar()) controller.show(WindowInsetsCompat.Type.statusBars());
        else controller.hide(WindowInsetsCompat.Type.statusBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void applyChrome() {
        int top = 0;
        int bottom = 0;
        if (lastInsets != null) {
            androidx.core.graphics.Insets bars = lastInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            top = bars.top;
            bottom = bars.bottom;
        } else {
            WindowInsets insets = root.getRootWindowInsets();
            if (insets != null) {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
        }
        int width = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int gutter = Math.max(dp(32), (width - dp(420)) / 2);
        homeColumn.setPadding(gutter, top + dp(18), gutter, bottom + dp(12));
        drawer.setPadding(gutter, top + dp(12), gutter, Math.max(bottom, dp(12)));
        settings.setPadding(gutter, top + dp(12), gutter, bottom + dp(20));
    }

    private boolean workbench() {
        return "workbench".equals(prefs.font());
    }

    private void applyType() {
        boolean bench = workbench();
        clockFace = bench ? workbenchFace : newsreaderFace;
        appFace = bench ? workbenchFace : outfitFace;
        clock.setTypeface(clockFace);
        clock.setLetterSpacing(bench ? 0f : -0.03f);
        clock.setIncludeFontPadding(bench);
        dateView.setTypeface(appFace);
        dateView.setLetterSpacing(bench ? 0.04f : 0.14f);
        hint.setTypeface(appFace);
        hint.setLetterSpacing(bench ? 0.06f : 0.16f);
        search.setTypeface(clockFace);
        search.setLetterSpacing(bench ? 0f : -0.02f);
        drawerHelp.setTypeface(appFace);
    }

    private boolean night() {
        return !"day".equals(prefs.theme());
    }

    private int textColor() {
        return night() ? 0xFFF4F0E8 : 0xFF1C1915;
    }

    private int dimColor() {
        return night() ? 0xFFCFC6B8 : 0xFF5E574C;
    }

    private int sheetColor() {
        return night() ? 0xF2141210 : 0xFFF6F1E8;
    }

    private float appSp() {
        if ("s".equals(prefs.textSize())) return 16f;
        if ("l".equals(prefs.textSize())) return 26f;
        return 20f;
    }

    private float clockSp() {
        if ("s".equals(prefs.textSize())) return 52f;
        if ("l".equals(prefs.textSize())) return 84f;
        return 68f;
    }

    private int gravity() {
        if ("left".equals(prefs.align())) return Gravity.START;
        if ("right".equals(prefs.align())) return Gravity.END;
        return Gravity.CENTER_HORIZONTAL;
    }

    private static String next(String[] values, String current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) return values[(i + 1) % values.length];
        }
        return values[0];
    }

    private static String backgroundLabel(String mode) {
        if ("mist".equals(mode)) return "Mist";
        if ("shore".equals(mode)) return "Shore";
        if ("night".equals(mode)) return "Night";
        if ("wallpaper".equals(mode)) return "Yours";
        return "Daily";
    }

    private static String sizeLabel(String size) {
        if ("s".equals(size)) return "Small";
        if ("l".equals(size)) return "Large";
        return "Medium";
    }

    private static String fontLabel(String font) {
        return "workbench".equals(font) ? "Workbench" : "Zen";
    }

    private static String alignLabel(String align) {
        if ("left".equals(align)) return "Left";
        if ("right".equals(align)) return "Right";
        return "Center";
    }

    private String swipeLabel(String id) {
        AppEntry entry = repo.find(id, true);
        return entry == null ? "None" : entry.label;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
