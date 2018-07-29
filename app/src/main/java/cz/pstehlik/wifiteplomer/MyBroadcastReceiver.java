package cz.pstehlik.wifiteplomer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class MyBroadcastReceiver extends BroadcastReceiver {

    private static MyBroadcastReceiver inst;

    public static void registerScreenReceiver(Context context) {
        if (inst == null) {
            inst = new MyBroadcastReceiver();
        }
        IntentFilter filter = new IntentFilter();
        //filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        //filter.addAction(Intent.ACTION_USER_UNLOCKED);
        context.getApplicationContext().registerReceiver(inst, filter);
    }

    public static void unregisterScreenReceiver(Context context) {
        if (inst != null) {
            context.getApplicationContext().unregisterReceiver(inst);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = (intent != null ? intent.getAction() : "");
        if (action.equals(Intent.ACTION_SCREEN_OFF)) {
            Log.d("MyBroadcastReceiver", "ACTION_SCREEN_OFF");
            WidgetProvider.turnAlarmOnOff(context, false);
        } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
            Log.d("MyBroadcastReceiver", "ACTION_SCREEN_ON");
            // WidgetProvider.turnAlarmOnOff(context, true);
        } else if (action.equals(Intent.ACTION_USER_PRESENT)) {
            Log.d("MyBroadcastReceiver", "ACTION_USER_PRESENT");
            WidgetProvider.turnAlarmOnOff(context, true);
        }
/*
        else if (action.equals(Intent.ACTION_USER_UNLOCKED)) {
            Log.d("MyBroadcastReceiver", "ACTION_USER_UNLOCKED");
            WidgetProvider.turnAlarmOnOff(context, true);
        }
*/
    }
}