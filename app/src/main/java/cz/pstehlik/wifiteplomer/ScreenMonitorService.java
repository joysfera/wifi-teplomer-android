package cz.pstehlik.wifiteplomer;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

public class ScreenMonitorService extends Service {
    private static BroadcastReceiver screenOffReceiver;
    private static BroadcastReceiver screenOnReceiver;
    private static BroadcastReceiver userPresentReceiver;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        registerScreenOffReceiver();
        registerScreenOnReceiver();
        registerUserPresentReceiver();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(screenOffReceiver);
        unregisterReceiver(screenOnReceiver);
        unregisterReceiver(userPresentReceiver);
    }

    private void registerScreenOffReceiver() {
        screenOffReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d("WidgetService", "ScreenOff.onReceive");
                WidgetProvider.turnAlarmOnOff(context, false);
            }

        };

        registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    private void registerScreenOnReceiver() {
        screenOnReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d("WidgetService", "ScreenOn.onReceive");
                KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                if (keyguardManager != null && !keyguardManager.inKeyguardRestrictedInputMode())
                    WidgetProvider.turnAlarmOnOff(context, true);
            }

        };

        registerReceiver(screenOnReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    private void registerUserPresentReceiver() {
        userPresentReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d("WidgetService", "UserPresentReceiver.onReceive");
                WidgetProvider.turnAlarmOnOff(context, true);
            }

        };

        registerReceiver(userPresentReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
    }
}
