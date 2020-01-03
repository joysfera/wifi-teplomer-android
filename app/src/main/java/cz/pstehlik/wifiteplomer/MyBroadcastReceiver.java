package cz.pstehlik.wifiteplomer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class MyBroadcastReceiver extends BroadcastReceiver {

    private static MyBroadcastReceiver inst;
    private boolean skipScreenOn = false;

    public static void registerScreenReceiver(Context context) {
        if (inst == null) {
            inst = new MyBroadcastReceiver();
            Log.d("MyBroadcastReceiver", "creating new MyBroadcastReceiver");
        } else {
            // better unregister first so it handles repeated calls to register
            unregisterScreenReceiver(context);
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        context.getApplicationContext().registerReceiver(inst, filter);
        Log.d("MyBroadcastReceiver", "registerScreenReceiver");
    }

    public static void unregisterScreenReceiver(Context context) {
        if (inst != null) {
            context.getApplicationContext().unregisterReceiver(inst);
            Log.d("MyBroadcastReceiver", "unregisterScreenReceiver");
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = (intent != null ? intent.getAction() : "");
        if (action == null) return;
        if (action.equals(Intent.ACTION_SCREEN_OFF)) {
            Log.d("MyBroadcastReceiver", "ACTION_SCREEN_OFF");
            // WidgetProvider.turnAlarmOnOff(context, false);
        } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
            Log.d("MyBroadcastReceiver", "ACTION_SCREEN_ON");
            if (!skipScreenOn)
                WidgetProvider.turnAlarmOnOff(context, true);
        } else if (action.equals(Intent.ACTION_USER_PRESENT)) {
            Log.d("MyBroadcastReceiver", "ACTION_USER_PRESENT");
            WidgetProvider.turnAlarmOnOff(context, true);
            skipScreenOn = true;
        }
    }
}