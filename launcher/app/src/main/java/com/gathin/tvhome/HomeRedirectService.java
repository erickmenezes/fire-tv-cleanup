package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.provider.Settings;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;

import java.util.List;

/**
 * Makes this launcher the home screen on Fire OS, which ignores the default-home setting.
 *
 * Fire OS never passes the Home key to accessibility services, so this watches windows instead: when Amazon's
 * home screen comes up, this launcher opens over it straight away. One exception: Amazon's home screen also
 * flashes up on the way into Settings, right after a window from Amazon's settings app, so a home screen that
 * follows a settings window within SETTINGS_GRACE_MS is left alone. Also opens the launcher once at boot.
 *
 * Fire OS 7 (Android 9) differs in two ways. Settings is a tab of Amazon's home screen, so the home screen is left
 * alone while Amazon's settings cards are on it, and leaving that tab with Home only moves focus. And Android 9
 * holds back starts from background apps for 5 s after Home, so a start that didn't land is retried after that.
 * Only system callers skip that hold (Fire OS also refuses adb connections from apps on the device), so while the
 * launcher is on its way, a cover with its background photo hides Amazon's home screen.
 *
 * Turned on and off from the launcher (Menu on the Settings button) when the app holds WRITE_SECURE_SETTINGS
 * (granted over adb: pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS), or over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/com.gathin.tvhome.HomeRedirectService
 *   settings put secure accessibility_enabled 1
 */
public class HomeRedirectService extends AccessibilityService {

    private static final String TAG = "HomeRedirect";
    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME = "HomeActivity";
    private static final String[] SETTINGS_APPS = {"com.amazon.tv.settings", "com.amazon.tv.quicksettings"};
    private static final long SETTINGS_GRACE_MS = 2000;     // measured: settings window -> home flash ~200 ms
    private static final long BOOT_WINDOW_MS = 3 * 60_000;  // service connecting this soon after boot = boot
    // Fire OS 7 shows Settings as a tab of Amazon's home screen; these cards are only there on that tab.
    private static final String SETTINGS_CARD = "com.amazon.tv.launcher:id/settings_card_view";
    // Android 9 holds back activity starts from background apps for 5 s after Home. The window event comes 1-2 s
    // after Home, so starts are retried from FIRST_RETRY_MS after it until one lands.
    private static final long APP_SWITCH_DELAY_MS = 5500;
    private static final long FIRST_RETRY_MS = 3000;
    private static final long RETRY_INTERVAL_MS = 750;
    private static final long SETTINGS_CHECK_DELAY_MS = 400;
    private static final long COVER_MAX_MS = 9000;          // retries and the cover stop after this
    private static final long COVER_HIDE_DELAY_MS = 150;    // lets the launcher draw its first frame first

    private long settingsSeenAt = -SETTINGS_GRACE_MS;
    private long redirectedAt = -APP_SWITCH_DELAY_MS;
    private boolean onAmazonHome;
    private ImageView cover;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable redirect = this::launch;
    private final Runnable hideCover = this::hideCover;
    private final Runnable retry = this::retry;
    private final Runnable checkLeftSettings = () -> {
        long now = SystemClock.uptimeMillis();
        if (!onAmazonHome || now - settingsSeenAt < SETTINGS_GRACE_MS || now - redirectedAt < APP_SWITCH_DELAY_MS) return;
        if (showingSettings()) return;
        Log.i(TAG, "left Amazon's Settings tab: opening launcher");
        redirect(now);
    };

    @Override
    protected void onServiceConnected() {
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) openLauncher();
    }

    @Override
    public void onDestroy() {
        hideCover();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName(), cls = event.getClassName();
        if (pkg == null || cls == null) return;
        long now = SystemClock.uptimeMillis();
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            // On Fire OS 7, Home pressed in Amazon's Settings tab only moves focus to the home tab: no new window.
            // The focus moves before the settings cards are removed, so look a moment later.
            if (!onAmazonHome || !AMAZON_LAUNCHER.contentEquals(pkg)) return;
            handler.removeCallbacks(checkLeftSettings);
            handler.postDelayed(checkLeftSettings, SETTINGS_CHECK_DELAY_MS);
            return;
        }
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        // Our own cover window is not a screen change.
        if (getPackageName().contentEquals(pkg) && !cls.toString().endsWith(".MainActivity")) return;
        for (String app : SETTINGS_APPS) if (pkg.toString().startsWith(app)) settingsSeenAt = now;
        handler.removeCallbacks(redirect);
        handler.removeCallbacks(retry);
        handler.removeCallbacks(checkLeftSettings);
        onAmazonHome = AMAZON_LAUNCHER.contentEquals(pkg) && cls.toString().contains(AMAZON_HOME);
        if (!onAmazonHome) {
            handler.postDelayed(hideCover, COVER_HIDE_DELAY_MS);
            return;
        }
        if (now - settingsSeenAt < SETTINGS_GRACE_MS) {
            Log.i(TAG, "home screen on the way into Settings: left alone");
            return;
        }
        if (showingSettings()) {
            Log.i(TAG, "back in Amazon's Settings tab: left alone");
            return;
        }
        Log.i(TAG, "home screen: opening launcher");
        redirect(now);
    }

    @Override
    public void onInterrupt() {
    }

    private void redirect(long now) {
        redirectedAt = now;
        handler.post(redirect);
        handler.postDelayed(retry, FIRST_RETRY_MS);
    }

    private boolean showingSettings() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> cards = root.findAccessibilityNodeInfosByViewId(SETTINGS_CARD);
        return cards != null && !cards.isEmpty();
    }

    private void retry() {
        if (!onAmazonHome || MainActivity.showing) return;
        Log.i(TAG, "launcher start was held back: retrying");
        openLauncher();
        if (SystemClock.uptimeMillis() - redirectedAt < COVER_MAX_MS) handler.postDelayed(retry, RETRY_INTERVAL_MS);
    }

    private void launch() {
        showCover();
        openLauncher();
    }

    /** Amazon's home screen hidden under the launcher's background photo until the launcher shows. */
    private void showCover() {
        handler.removeCallbacks(hideCover);
        handler.postDelayed(hideCover, COVER_MAX_MS);
        if (cover != null) return;
        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setImageResource(MainActivity.background());
        cover.setForeground(getDrawable(R.drawable.bg_scrim));   // same shading as the launcher's photo
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.OPAQUE);
        getSystemService(WindowManager.class).addView(cover, lp);
    }

    private void hideCover() {
        handler.removeCallbacks(hideCover);
        if (cover == null) return;
        getSystemService(WindowManager.class).removeView(cover);
        cover = null;
    }

    private void openLauncher() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    // ---- on/off from the launcher's UI -----------------------------------------------------------

    static boolean isEnabled(Context ctx) {
        String on = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return on != null && on.contains(component(ctx).flattenToString());
    }

    /** False when the app lacks WRITE_SECURE_SETTINGS. */
    static boolean setEnabled(Context ctx, boolean on) {
        try {
            Settings.Secure.putString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    on ? component(ctx).flattenToString() : "");
            Settings.Secure.putInt(ctx.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, on ? 1 : 0);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    private static ComponentName component(Context ctx) {
        return new ComponentName(ctx, HomeRedirectService.class);
    }
}
