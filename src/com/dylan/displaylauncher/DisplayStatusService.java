package com.dylan.displaylauncher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.ComponentName;
import android.os.Build;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;

/** Optional window-owner reader: no text traversal, gestures, network or stored window data. */
public final class DisplayStatusService extends AccessibilityService {
    private static DisplayStatusService connected;
    @Override protected void onServiceConnected() { connected = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        if (connected == this) connected = null;
        super.onDestroy();
    }
    static boolean isEnabled(Context context) {
        // Authorization is distinct from binding (for example, automation can temporarily suspend a service).
        try {
            String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled != null) {
                ComponentName expected = new ComponentName(context, DisplayStatusService.class);
                for (String value : enabled.split(":")) {
                    if (expected.equals(ComponentName.unflattenFromString(value))) return true;
                }
                return false;
            }
        } catch (SecurityException unavailable) { /* Use the public manager as a compatibility fallback. */ }
        AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
        if (manager == null) return false;
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null
                && context.getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)
                && DisplayStatusService.class.getName().equals(info.getResolveInfo().serviceInfo.name)) return true;
        }
        return false;
    }
    static final class Snapshot {
        final boolean connected, hasApp;
        final String packageName;
        Snapshot(boolean connected, boolean hasApp, String packageName) {
            this.connected = connected; this.hasApp = hasApp; this.packageName = packageName;
        }
    }
    static Snapshot read(int displayId) {
        DisplayStatusService service = connected;
        if (service == null || Build.VERSION.SDK_INT < 30) return new Snapshot(false, false, null);
        try {
            SparseArray<List<AccessibilityWindowInfo>> all = service.getWindowsOnAllDisplays();
            List<AccessibilityWindowInfo> windows = all.get(displayId);
            boolean hasApp = false;
            String owner = null;
            try {
                if (windows != null) {
                    // Android orders windows by descending layer. Ignore keyboards and system chrome.
                    for (AccessibilityWindowInfo window : windows) {
                        if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                        hasApp = true;
                        AccessibilityNodeInfo root = window.getRoot();
                        if (root != null) {
                            try {
                                CharSequence pkg = root.getPackageName();
                                if (pkg != null) owner = pkg.toString();
                            } finally { root.recycle(); }
                        }
                        // Never label an underlying window as current when the top one is inaccessible.
                        break;
                    }
                }
                return new Snapshot(true, hasApp, owner);
            } finally {
                for (int i = 0; i < all.size(); i++) {
                    for (AccessibilityWindowInfo window : all.valueAt(i)) window.recycle();
                }
            }
        } catch (RuntimeException error) { return new Snapshot(false, false, null); }
    }
}
