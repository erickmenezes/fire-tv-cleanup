package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The launcher's own Settings screen: one card per section of Fire OS settings.
 *
 * Fire OS 7 shows its settings grid as a tab of Amazon's home screen, but most sections are separate screens
 * that open directly. Those cards skip Amazon's home screen. The rest need an Amazon-only permission
 * (com.amazon.tv.permission.LAUNCHER_SETTINGS), so their cards open Amazon's settings grid instead.
 */
public class SettingsActivity extends Activity {

    private static final String AMAZON_SETTINGS = "com.amazon.tv.settings.v2";
    private static final float FOCUS_SCALE = 1.06f;

    private TextView homeSubtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        ((ImageView) findViewById(R.id.bgPhoto)).setImageResource(MainActivity.background());

        GridLayout cards = findViewById(R.id.cards);
        add(cards, R.drawable.ic_wifi, R.string.set_network, R.string.set_network_sub,
                section(".tv.network.NetworkActivity"));
        add(cards, R.drawable.ic_apps, R.string.set_apps, R.string.set_apps_sub,
                section(".tv.applications.ApplicationsActivity"));
        add(cards, R.drawable.ic_bluetooth, R.string.set_remotes, R.string.set_remotes_sub,
                section(".tv.controllers_bluetooth_devices.ControllersAndBluetoothActivity"));
        add(cards, R.drawable.ic_info, R.string.set_device, R.string.set_device_sub,
                section(".tv.device.DeviceActivity"));
        add(cards, R.drawable.ic_accessibility, R.string.set_accessibility, R.string.set_accessibility_sub,
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        // Amazon-only sections: through Amazon's settings grid.
        add(cards, R.drawable.ic_tv, R.string.set_display, R.string.set_display_sub,
                new Intent(Settings.ACTION_SETTINGS));
        add(cards, R.drawable.ic_tune, R.string.set_preferences, R.string.set_preferences_sub,
                new Intent(Settings.ACTION_SETTINGS));
        add(cards, R.drawable.ic_person, R.string.set_account, R.string.set_account_sub,
                new Intent(Settings.ACTION_SETTINGS));

        // Switches the Home redirect, like Menu on the home screen's Settings button.
        View home = add(cards, R.drawable.ic_home, R.string.set_home, 0, null);
        homeSubtitle = home.findViewById(R.id.subtitle);
        home.setOnClickListener(v -> {
            boolean on = HomeRedirectService.isEnabled(this);
            if (!HomeRedirectService.setEnabled(this, !on)) {
                Toast.makeText(this, R.string.redirect_needs_permission, Toast.LENGTH_LONG).show();
            }
            updateHomeCard();
        });

        cards.getChildAt(0).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHomeCard();
    }

    private void updateHomeCard() {
        boolean on = HomeRedirectService.isEnabled(this);
        homeSubtitle.setText(on ? R.string.set_home_on_sub : R.string.set_home_off_sub);
    }

    private static Intent section(String activity) {
        return new Intent().setComponent(new ComponentName(AMAZON_SETTINGS, AMAZON_SETTINGS + activity));
    }

    private View add(GridLayout cards, int icon, int title, int subtitle, Intent target) {
        View card = getLayoutInflater().inflate(R.layout.item_setting, cards, false);
        ((ImageView) card.findViewById(R.id.icon)).setImageResource(icon);
        if (title != 0) ((TextView) card.findViewById(R.id.title)).setText(title);
        if (subtitle != 0) ((TextView) card.findViewById(R.id.subtitle)).setText(subtitle);
        if (target != null) card.setOnClickListener(v -> open(target));
        card.setOnFocusChangeListener((v, focused) -> v.animate()
                .scaleX(focused ? FOCUS_SCALE : 1f).scaleY(focused ? FOCUS_SCALE : 1f)
                .setDuration(160).setInterpolator(new DecelerateInterpolator()).start());
        cards.addView(card);
        return card;
    }

    private void open(Intent intent) {
        // Amazon's settings screens share one task of their own: start it fresh so Back from a section
        // returns here instead of walking through screens left in it earlier.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            // A section that moved or got locked down in a Fire OS update: fall back to the full grid.
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }
}
