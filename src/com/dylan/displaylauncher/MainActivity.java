package com.dylan.displaylauncher;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.LruCache;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String TAG = "DisplayLauncher";
    private static final int INK = 0xff172b3a;
    private static final int MUTED = 0xff607080;
    private static final int ACCENT = 0xff087f8c;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final ArrayList<AppEntry> apps = new ArrayList<>();
    private final ArrayList<AppEntry> filtered = new ArrayList<>();
    private final ArrayList<Display> displays = new ArrayList<>();
    private final LruCache<String, Drawable> icons = new LruCache<>(40);
    private DisplayManager displayManager;
    private TextView displayTitle, displayDetail, historyTitle, historyNote, count, empty;
    private ImageView historyIcon;
    private Button monitorAccess;
    private String lastStatusKey = "";
    private final Runnable statusPoll = new Runnable() {
        @Override public void run() {
            if (!isDestroyed() && !isFinishing()) {
                updateHistory();
                handler.postDelayed(this, 1500);
            }
        }
    };
    private Spinner displayPicker;
    private EditText search;
    private AppAdapter adapter;
    private boolean loading = true, launching = false, loadFailed = false;
    private int selectedDisplayId = -1;
    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        public void onDisplayAdded(int id) { updateDisplays(); }
        public void onDisplayRemoved(int id) { updateDisplays(); }
        public void onDisplayChanged(int id) { updateDisplays(); }
    };

    @Override protected void attachBaseContext(Context base) {
        String fallback = Locale.getDefault().getLanguage().equals("zh") ? "zh" : "en";
        String language = base.getSharedPreferences("preferences", MODE_PRIVATE).getString("language", fallback);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(language));
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        displayManager = getSystemService(DisplayManager.class);
        buildInterface();
        updateDisplays();
        loadApps();
    }

    @Override protected void onStart() {
        super.onStart();
        displayManager.registerDisplayListener(displayListener, handler);
        updateDisplays();
        handler.removeCallbacks(statusPoll);
        handler.post(statusPoll);
    }

    @Override protected void onStop() {
        handler.removeCallbacks(statusPoll);
        displayManager.unregisterDisplayListener(displayListener);
        super.onStop();
    }

    @Override protected void onDestroy() {
        loader.shutdownNow();
        icons.evictAll();
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        updateDisplays();
        loadApps();
    }

    private void buildInterface() {
        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(0xfff5f7fa);
        outer.setFitsSystemWindows(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(28), dp(22), dp(28), dp(12));
        int width = Math.min(getResources().getDisplayMetrics().widthPixels, dp(1040));
        outer.addView(content, new FrameLayout.LayoutParams(width, -1, Gravity.CENTER_HORIZONTAL));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.header_logo);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        markParams.setMarginEnd(dp(14));
        header.addView(mark, markParams);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.addView(text(getString(R.string.app_name), 25, INK, true));
        TextView subtitle = text(getString(R.string.subtitle), 14, MUTED, false);
        subtitle.setPadding(0, dp(4), 0, 0);
        heading.addView(subtitle);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button language = smallButton(getString(R.string.language_button));
        language.setId(1001);
        language.setContentDescription(getString(R.string.language_description));
        language.setOnClickListener(view -> {
            String current = getResources().getConfiguration().getLocales().get(0).getLanguage();
            getSharedPreferences("preferences", MODE_PRIVATE).edit()
                .putString("language", current.equals("zh") ? "en" : "zh").apply();
            recreate();
        });
        header.addView(language);
        content.addView(header);

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setPadding(dp(18), dp(14), dp(18), dp(14));
        status.setBackground(rounded(Color.WHITE, 14, 0xffe1e7ec));
        LinearLayout statusLine = new LinearLayout(this);
        statusLine.setGravity(Gravity.CENTER_VERTICAL);
        displayTitle = text("", 16, INK, true);
        statusLine.addView(displayTitle, new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = smallButton(getString(R.string.refresh));
        refresh.setId(1002);
        refresh.setOnClickListener(view -> { updateDisplays(); icons.evictAll(); loadApps(); });
        statusLine.addView(refresh);
        status.addView(statusLine);
        displayDetail = text("", 13, MUTED, false);
        status.addView(displayDetail);
        displayPicker = new Spinner(this);
        displayPicker.setContentDescription(getString(R.string.target_display));
        displayPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < displays.size()) selectedDisplayId = displays.get(position).getDisplayId();
                updateHistory();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        status.addView(displayPicker, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout history = new LinearLayout(this);
        history.setGravity(Gravity.CENTER_VERTICAL);
        history.setPadding(0, dp(14), 0, 0);
        historyIcon = new ImageView(this);
        historyIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams historyIconParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        historyIconParams.setMarginEnd(dp(12));
        history.addView(historyIcon, historyIconParams);
        LinearLayout historyLabels = new LinearLayout(this);
        historyLabels.setOrientation(LinearLayout.VERTICAL);
        historyTitle = text("", 15, INK, true);
        historyLabels.addView(historyTitle);
        historyNote = text(getString(R.string.history_note), 12, MUTED, false);
        historyNote.setPadding(0, dp(3), 0, 0);
        historyLabels.addView(historyNote);
        history.addView(historyLabels, new LinearLayout.LayoutParams(0, -2, 1));
        monitorAccess = smallButton(getString(R.string.enable_live));
        monitorAccess.setOnClickListener(view -> openMonitorSettings());
        LinearLayout.LayoutParams monitorParams = new LinearLayout.LayoutParams(-2, dp(48));
        monitorParams.setMarginStart(dp(12));
        history.addView(monitorAccess, monitorParams);
        status.addView(history);
        updateHistory();
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(22);
        content.addView(status, statusParams);

        search = new EditText(this);
        search.setId(1003);
        search.setHint(R.string.search_hint);
        search.setContentDescription(getString(R.string.search_hint));
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setTextColor(INK);
        search.setHintTextColor(MUTED);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE
                || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0);
                search.clearFocus();
                return true;
            }
            return false;
        });
        search.setPadding(dp(18), 0, dp(18), 0);
        search.setBackground(rounded(Color.WHITE, 12, 0xffd9e2e8));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int before, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { filterApps(); }
            public void afterTextChanged(Editable s) { }
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(52));
        searchParams.topMargin = dp(16);
        content.addView(search, searchParams);

        count = text(getString(R.string.loading), 13, MUTED, false);
        count.setPadding(dp(3), dp(15), 0, dp(9));
        content.addView(count);
        FrameLayout listContainer = new FrameLayout(this);
        listContainer.setBackground(rounded(Color.WHITE, 14, 0xffe1e7ec));
        ListView list = new ListView(this);
        list.setId(1004);
        list.setClipToPadding(false);
        list.setPadding(dp(8), dp(4), dp(8), dp(4));
        list.setDivider(null);
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < filtered.size()) launchApp(filtered.get(position));
        });
        listContainer.addView(list, new FrameLayout.LayoutParams(-1, -1));
        empty = text(getString(R.string.loading), 15, MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(12), dp(24), dp(12));
        listContainer.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        list.setEmptyView(empty);
        content.addView(listContainer, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout footerRow = new LinearLayout(this);
        footerRow.setGravity(Gravity.CENTER_VERTICAL);
        footerRow.setPadding(0, dp(12), 0, 0);
        TextView footer = text(getString(R.string.footer), 12, MUTED, false);
        footer.setPadding(0, 0, dp(12), 0);
        footerRow.addView(footer, new LinearLayout.LayoutParams(0, -2, 1));
        Button home = smallButton(getString(R.string.go_home));
        home.setId(1005);
        home.setContentDescription(getString(R.string.go_home));
        home.setOnClickListener(view -> returnToDesktop());
        footerRow.addView(home);
        content.addView(footerRow);
        content.setFocusableInTouchMode(true);
        content.requestFocus();
        setContentView(outer);
    }

    private void updateDisplays() {
        if (displayManager == null || displayTitle == null) return;
        boolean supported = getPackageManager().hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS);
        displays.clear();
        if (supported) {
            for (Display display : displayManager.getDisplays()) {
                if (display.getDisplayId() != Display.DEFAULT_DISPLAY && display.isValid()
                    && (display.getFlags() & Display.FLAG_PRIVATE) == 0) displays.add(display);
            }
        }
        Collections.sort(displays, (left, right) -> Integer.compare(left.getDisplayId(), right.getDisplayId()));
        int index = 0;
        for (int i = 0; i < displays.size(); i++) {
            if (displays.get(i).getDisplayId() == selectedDisplayId) index = i;
        }
        if (displays.isEmpty()) {
            selectedDisplayId = -1;
            displayTitle.setText(R.string.display_missing);
            displayTitle.setTextColor(0xff8b5b14);
            displayDetail.setText(supported ? R.string.display_connect_help : R.string.display_unsupported);
            displayPicker.setVisibility(View.GONE);
        } else {
            selectedDisplayId = displays.get(index).getDisplayId();
            displayTitle.setText(R.string.display_ready);
            displayTitle.setTextColor(0xff237445);
            displayDetail.setText(displays.size() == 1 ? displayLabel(displays.get(0)) : getString(R.string.target_display));
            ArrayList<String> labels = new ArrayList<>();
            for (int i = 0; i < displays.size(); i++) {
                labels.add(getString(R.string.display_choice, i + 1, displayLabel(displays.get(i))));
            }
            ArrayAdapter<String> pickerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
            pickerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            displayPicker.setAdapter(pickerAdapter);
            displayPicker.setSelection(index);
            displayPicker.setVisibility(displays.size() > 1 ? View.VISIBLE : View.GONE);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        updateHistory();
    }

    private String displayLabel(Display display) {
        String name = display.getName();
        if (name == null || name.trim().isEmpty()) name = getString(R.string.display_fallback);
        Display.Mode mode = display.getMode();
        return getString(R.string.display_format, name, mode.getPhysicalWidth(), mode.getPhysicalHeight());
    }

    private void updateHistory() {
        if (historyTitle == null || historyNote == null || monitorAccess == null) return;
        String pkg = getSharedPreferences("preferences", MODE_PRIVATE).getString("last_package", "");
        String label = getSharedPreferences("preferences", MODE_PRIVATE).getString("last_label", "");
        boolean enabled = Build.VERSION.SDK_INT >= 30 && DisplayStatusService.isEnabled(this);
        String title = pkg.isEmpty() ? getString(R.string.no_history) : getString(R.string.last_opened, label);
        String note = getString(R.string.history_note);
        if (enabled) {
            pkg = "";
            note = getString(R.string.live_note);
            if (selectedDisplayId < 0) title = getString(R.string.display_missing);
            else {
                DisplayStatusService.Snapshot snapshot = DisplayStatusService.read(selectedDisplayId);
                if (!snapshot.connected) title = getString(R.string.live_connecting);
                else if (!snapshot.hasApp) title = getString(R.string.live_empty);
                else if (snapshot.packageName == null) title = getString(R.string.live_unknown);
                else {
                    pkg = snapshot.packageName;
                    try { label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
                    catch (PackageManager.NameNotFoundException error) { label = pkg; }
                    title = getString(R.string.live_app, label);
                }
            }
        } else if (Build.VERSION.SDK_INT < 30) note = getString(R.string.live_unsupported) + " " + note;
        String key = title + "\n" + note + "\n" + pkg + "\n" + enabled;
        if (key.equals(lastStatusKey)) return;
        lastStatusKey = key;
        historyTitle.setText(title);
        historyNote.setText(note);
        monitorAccess.setEnabled(Build.VERSION.SDK_INT >= 30);
        monitorAccess.setText(enabled ? R.string.manage_live : R.string.enable_live);
        Log.i(TAG, "Status display=" + selectedDisplayId + " live=" + enabled + " package=" + pkg);
        historyIcon.setVisibility(pkg.isEmpty() ? View.GONE : View.VISIBLE);
        if (!pkg.isEmpty()) {
            try { historyIcon.setImageDrawable(getPackageManager().getApplicationIcon(pkg)); }
            catch (PackageManager.NameNotFoundException error) { historyIcon.setImageDrawable(getPackageManager().getDefaultActivityIcon()); }
        }
    }

    private void openMonitorSettings() {
        Runnable open = () -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            try { startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()); }
            catch (RuntimeException error) { Log.e(TAG, "Accessibility settings unavailable", error); showError(R.string.error_generic); }
        };
        if (DisplayStatusService.isEnabled(this)) { open.run(); return; }
        new AlertDialog.Builder(this).setTitle(R.string.monitor_disclosure_title)
            .setMessage(R.string.monitor_disclosure)
            .setNegativeButton(R.string.not_now, null)
            .setPositiveButton(R.string.open_settings, (dialog, which) -> open.run()).show();
    }

    private void loadApps() {
        if (isDestroyed() || loader.isShutdown()) return;
        loading = true;
        loadFailed = false;
        filterApps();
        loader.execute(() -> {
            ArrayList<AppEntry> result = new ArrayList<>();
            boolean failed = false;
            try {
                PackageManager pm = getPackageManager();
                Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> resolved = pm.queryIntentActivities(launcherIntent, 0);
                Set<String> seen = new HashSet<>();
                for (ResolveInfo info : resolved) {
                    String pkg = info.activityInfo.packageName;
                    if (pkg.equals(getPackageName()) || !seen.add(pkg) || !info.activityInfo.exported) continue;
                    if (pm.getLaunchIntentForPackage(pkg) == null) continue;
                    CharSequence label = info.loadLabel(pm);
                    result.add(new AppEntry(pkg, label == null ? pkg : label.toString()));
                }
                HashMap<String, Integer> labelCounts = new HashMap<>();
                for (AppEntry entry : result) labelCounts.put(entry.label, labelCounts.getOrDefault(entry.label, 0) + 1);
                for (AppEntry entry : result) entry.duplicateLabel = labelCounts.get(entry.label) > 1;
                Collator collator = Collator.getInstance(getResources().getConfiguration().getLocales().get(0));
                Collections.sort(result, (left, right) -> {
                    int compared = collator.compare(left.label, right.label);
                    return compared != 0 ? compared : left.packageName.compareTo(right.packageName);
                });
            } catch (RuntimeException error) {
                Log.e(TAG, "Loading apps failed", error);
                failed = true;
            }
            final boolean didFail = failed;
            handler.post(() -> {
                if (isDestroyed() || isFinishing()) return;
                apps.clear(); apps.addAll(result);
                loading = false; loadFailed = didFail;
                filterApps();
            });
        });
    }

    private void filterApps() {
        if (search == null || count == null || adapter == null) return;
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (AppEntry entry : apps) {
            if (entry.label.toLowerCase(Locale.ROOT).contains(query)
                || entry.packageName.toLowerCase(Locale.ROOT).contains(query)) filtered.add(entry);
        }
        count.setText(loading ? getString(R.string.loading) : getString(query.isEmpty() ? R.string.apps_count : R.string.results_count, filtered.size()));
        empty.setText(loading ? R.string.loading : loadFailed ? R.string.list_failed : query.isEmpty() ? R.string.empty_apps : R.string.empty_search);
        adapter.notifyDataSetChanged();
    }

    private void launchApp(AppEntry entry) {
        if (launching) return;
        int requestedDisplayId = selectedDisplayId;
        updateDisplays();
        if (requestedDisplayId >= 0 && selectedDisplayId != requestedDisplayId) {
            showError(R.string.error_display); return;
        }
        Display target = displayManager.getDisplay(selectedDisplayId);
        if (target == null || !target.isValid() || target.getDisplayId() == Display.DEFAULT_DISPLAY) {
            showError(R.string.error_display); return;
        }
        Intent intent = getPackageManager().getLaunchIntentForPackage(entry.packageName);
        if (intent == null) { showError(R.string.error_app_missing); loadApps(); return; }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            if (Build.VERSION.SDK_INT >= 29 && !getSystemService(ActivityManager.class).isActivityStartAllowedOnDisplay(this, target.getDisplayId(), intent)) {
                showError(R.string.error_permission); return;
            }
            launching = true;
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0);
            ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(target.getDisplayId());
            startActivity(intent, options.toBundle());
            getSharedPreferences("preferences", MODE_PRIVATE).edit()
                .putString("last_package", entry.packageName).putString("last_label", entry.label).apply();
            updateHistory();
            search.clearFocus();
            launching = false;
            Log.i(TAG, "Requested " + entry.packageName + " on display " + target.getDisplayId());
        } catch (ActivityNotFoundException error) {
            launching = false; Log.e(TAG, "App unavailable", error); showError(R.string.error_app_missing); return;
        } catch (SecurityException error) {
            launching = false; Log.e(TAG, "Launch denied", error); showError(R.string.error_permission); return;
        } catch (IllegalArgumentException error) {
            launching = false; Log.e(TAG, "Invalid display", error); showError(R.string.error_display); return;
        } catch (RuntimeException error) {
            launching = false; Log.e(TAG, "Launch failed", error); showError(R.string.error_generic); return;
        }
    }

    private void returnToDesktop() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(home, ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle());
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not bring desktop forward", error);
        }
        finishAndRemoveTask();
    }

    private void showError(int message) {
        new AlertDialog.Builder(this).setTitle(R.string.error_title).setMessage(message)
            .setPositiveButton(R.string.ok, null).show();
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return filtered.size(); }
        @Override public AppEntry getItem(int position) { return filtered.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            Row holder;
            if (recycled == null) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(14), dp(10), dp(14), dp(10));
                row.setMinimumHeight(dp(76));
                holder = new Row();
                holder.icon = new ImageView(MainActivity.this);
                holder.icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                row.addView(holder.icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
                LinearLayout labels = new LinearLayout(MainActivity.this);
                labels.setOrientation(LinearLayout.VERTICAL);
                holder.name = text("", 17, INK, false);
                holder.detail = text("", 12, MUTED, false);
                labels.addView(holder.name); labels.addView(holder.detail);
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
                labelParams.setMarginStart(dp(18));
                row.addView(labels, labelParams);
                TextView arrow = text("↗", 24, ACCENT, false);
                arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                row.addView(arrow);
                row.setTag(holder); recycled = row;
            } else holder = (Row) recycled.getTag();
            AppEntry entry = getItem(position);
            holder.name.setText(entry.label);
            holder.detail.setText(entry.duplicateLabel ? entry.packageName : "");
            holder.detail.setVisibility(entry.duplicateLabel ? View.VISIBLE : View.GONE);
            recycled.setContentDescription(getString(R.string.row_action, entry.label));
            recycled.setAlpha(displays.isEmpty() ? 0.5f : 1f);
            Drawable icon = icons.get(entry.packageName);
            if (icon == null) {
                try { icon = getPackageManager().getApplicationIcon(entry.packageName); }
                catch (PackageManager.NameNotFoundException error) { icon = getPackageManager().getDefaultActivityIcon(); }
                icons.put(entry.packageName, icon);
            }
            holder.icon.setImageDrawable(icon);
            return recycled;
        }
    }

    private static final class AppEntry {
        final String packageName, label;
        boolean duplicateLabel;
        AppEntry(String packageName, String label) { this.packageName = packageName; this.label = label; }
    }
    private static final class Row { ImageView icon; TextView name, detail; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label); button.setAllCaps(false); button.setTextSize(14);
        button.setTextColor(ACCENT); button.setMinWidth(dp(80));
        button.setMinimumWidth(dp(80)); button.setMinHeight(dp(48));
        button.setBackground(rounded(0xffe9f3f4, 10, Color.TRANSPARENT));
        button.setPadding(dp(14), 0, dp(14), 0);
        return button;
    }
    private GradientDrawable rounded(int fill, int radius, int border) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill); drawable.setCornerRadius(dp(radius));
        if (border != Color.TRANSPARENT) drawable.setStroke(dp(1), border);
        return drawable;
    }
}
