package com.gathin.tvhome;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Spots the Home button in the system log, about a second before Amazon's home screen is drawn.
 *
 * Accessibility events only report Amazon's home screen once it is about to show, so the cover over it comes
 * late. When Home is pressed, ActivityManager logs the start of Amazon's home activity from the system user
 * (uid 1000) right away. Reading the log needs READ_LOGS, granted over adb by build-and-install.sh; without it
 * this does nothing and the redirect relies on accessibility events alone.
 */
final class HomeLogWatcher {

    private static final String TAG = "HomeLogWatcher";
    private static final String HOME_START = "cat=[android.intent.category.HOME]";
    private static final String FROM_SYSTEM = "from uid 1000";   // the Home button; Settings starts it from its own uid
    private static final long RESTART_DELAY_MS = 2000;

    private final Context ctx;
    private final String launcherPackage;
    private final Runnable onHome;
    private volatile boolean running;
    private boolean toldNoPermission;
    private volatile Process logcat;

    HomeLogWatcher(Context ctx, String launcherPackage, Runnable onHome) {
        this.ctx = ctx;
        this.launcherPackage = launcherPackage;
        this.onHome = onHome;
    }

    /** Starts watching once READ_LOGS is granted; safe to call again. onHome runs on the watcher's thread. */
    void start() {
        if (running) return;
        if (ctx.checkSelfPermission(Manifest.permission.READ_LOGS) != PackageManager.PERMISSION_GRANTED) {
            if (!toldNoPermission) Log.i(TAG, "no READ_LOGS: relying on accessibility events");
            toldNoPermission = true;
            return;
        }
        running = true;
        Thread t = new Thread(this::watch, TAG);
        t.setDaemon(true);
        t.start();
    }

    void stop() {
        running = false;
        Process p = logcat;
        if (p != null) p.destroy();
    }

    private void watch() {
        while (running) {
            try {
                // -T 1: from now on, not the backlog. -s: ActivityManager's info lines only.
                logcat = new ProcessBuilder("logcat", "-v", "brief", "-T", "1", "-s", "ActivityManager:I")
                        .redirectErrorStream(true).start();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(logcat.getInputStream()))) {
                    String line;
                    while (running && (line = in.readLine()) != null) {
                        if (line.contains("START u0") && line.contains(HOME_START) && line.contains(FROM_SYSTEM)
                                && line.contains(launcherPackage)) {
                            onHome.run();
                        }
                    }
                }
            } catch (IOException e) {
                Log.i(TAG, "logcat failed: " + e);
            }
            if (!running) return;
            try {
                Thread.sleep(RESTART_DELAY_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
