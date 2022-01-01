package ru.tsar_ioann.smarthome.screens;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessaging;

import ru.tsar_ioann.smarthome.*;

public class AppSettings extends BaseScreen {
    private static final String LOG_TAG = "AppSettings";

    private final Activity activity;
    private final SharedPreferences phoneFindSettings;

    public AppSettings(CommonData commonData) {
        super(commonData);

        activity = commonData.getActivity();
        final TextView txtFirebaseToken = activity.findViewById(R.id.txtFirebaseToken);
        final RadioButton rbSoundNastyButLoud = activity.findViewById(R.id.rbSoundNastyButLoud);
        final RadioButton rbSoundAsInMiFit = activity.findViewById(R.id.rbSoundAsInMiFit);

        phoneFindSettings = activity.getSharedPreferences(PhoneFindService.SETTINGS_NAME, Context.MODE_PRIVATE);
        final int soundId = phoneFindSettings.getInt(PhoneFindService.SETTINGS_KEY_SOUND, 0);

        switch (soundId) {
            case PhoneFindService.SETTINGS_VALUE_SOUND_NASTY:
                rbSoundNastyButLoud.setChecked(true);
                break;
            case PhoneFindService.SETTINGS_VALUE_SOUND_MI_FIT:
                rbSoundAsInMiFit.setChecked(true);
                break;
        }
        rbSoundNastyButLoud.jumpDrawablesToCurrentState();
        rbSoundAsInMiFit.jumpDrawablesToCurrentState();

        rbSoundNastyButLoud.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                setPhoneFindSound(PhoneFindService.SETTINGS_VALUE_SOUND_NASTY);
            }
        });
        rbSoundAsInMiFit.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                setPhoneFindSound(PhoneFindService.SETTINGS_VALUE_SOUND_MI_FIT);
            }
        });

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.d(LOG_TAG, "Fetching firebase token failed", task.getException());
                txtFirebaseToken.setText(R.string.failed_to_obtain);
                txtFirebaseToken.setOnClickListener(null);
                return;
            }

            final String firebaseToken = task.getResult();
            Log.d(LOG_TAG, "Firebase token: " + firebaseToken);
            txtFirebaseToken.setText(firebaseToken);
            txtFirebaseToken.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("firebase token", firebaseToken);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(activity, R.string.firebase_token_was_copied, Toast.LENGTH_SHORT).show();
            });
        });

        if (Build.MANUFACTURER.equalsIgnoreCase("xiaomi")) {
            Button btnGoToAutostartSettings = activity.findViewById(R.id.btnGoToAutostartSettings);
            btnGoToAutostartSettings.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent();
                    intent.setComponent(new ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    ));
                    activity.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.d(LOG_TAG, "Failed to start MIUI securitycenter");
                    Toast.makeText(activity, R.string.failed_to_open_needed_settings, Toast.LENGTH_LONG).show();
                }
            });
            btnGoToAutostartSettings.setVisibility(View.VISIBLE);
            btnGoToAutostartSettings.jumpDrawablesToCurrentState();

            Button btnGoToAppsPerformanceSettings = activity.findViewById(R.id.btnGoToAppsPerformanceSettings);
            btnGoToAppsPerformanceSettings.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent();
                    intent.setClassName(
                            "com.miui.powerkeeper",
                            "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    );
                    intent.putExtra("package_name", activity.getPackageName());
                    intent.putExtra("package_label", tr(R.string.app_name));
                    activity.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.d(LOG_TAG, "Failed to start MIUI powerkeeper");
                    Toast.makeText(activity, R.string.failed_to_open_needed_settings, Toast.LENGTH_LONG).show();
                }
            });
            btnGoToAppsPerformanceSettings.setVisibility(View.VISIBLE);
            btnGoToAppsPerformanceSettings.jumpDrawablesToCurrentState();
        }
    }

    private void setPhoneFindSound(int soundId) {
        SharedPreferences.Editor editor = phoneFindSettings.edit();
        editor.putInt(PhoneFindService.SETTINGS_KEY_SOUND, soundId);
        editor.apply();
        Toast.makeText(activity, R.string.device_search_sound_changed, Toast.LENGTH_SHORT).show();
    }

    @Override
    public int getViewFlipperChildId() {
        return 9;
    }
}
