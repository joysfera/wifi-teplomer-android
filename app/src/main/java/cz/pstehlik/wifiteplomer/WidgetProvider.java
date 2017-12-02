package cz.pstehlik.wifiteplomer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;

public class WidgetProvider extends AppWidgetProvider {
    public static String UPDATE_LIST = "UPDATE_LIST";
    static long lastForcedUpdateAt = 0;

    public static void turnAlarmOnOff(Context context, boolean turnOn) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        PendingIntent pendingIntent = myUpdateIntent(context);

        if (turnOn) {
            long interval = 3 * 60 * 1000;
            long currentTime = SystemClock.elapsedRealtime();
            alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, currentTime + interval, interval, pendingIntent);
            Log.d("WidgetProvider", "Alarm set");
            if (currentTime > lastForcedUpdateAt + 30 * 1000) {
                try {
                    pendingIntent.send();
                    lastForcedUpdateAt = currentTime;
                    Log.d("WidgetProvider", "Forced update");
                } catch (PendingIntent.CanceledException e) {
                    Log.wtf("WidgetProvider", "Exception in pendingIntent.send()");
                }
            }
        } else {
            alarmManager.cancel(pendingIntent);
            Log.d("WidgetProvider", "Alarm disabled");
        }
    }

    private static PendingIntent myUpdateIntent(Context context) {
        Intent in = new Intent(context, WidgetProvider.class);
        in.setAction(UPDATE_LIST);
        return PendingIntent.getBroadcast(context, 0, in, 0);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);

        Log.d("WidgetProvider", "onEnabled()");

        turnAlarmOnOff(context, true);
        context.startService(new Intent(context, ScreenMonitorService.class));
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

        Log.d("WidgetProvider", "onUpdate(" + appWidgetIds.length + ")");
        Intent svcIntent = new Intent(context, WidgetService.class);
        //svcIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
        //svcIntent.setData(Uri.parse(svcIntent .toUri(Intent.URI_INTENT_SCHEME)));

        RemoteViews widget = new RemoteViews(context.getPackageName(), R.layout.widget);
        widget.setRemoteAdapter(R.id.temperatures, svcIntent);

        Intent clickIntent = new Intent(Intent.ACTION_VIEW); // new Intent(context, MainActivity.class);
        clickIntent.setData(Uri.parse(AppWidgetViewsFactory.getTeplotyInfoUrl("profile.php", context)));
        PendingIntent clickPI = PendingIntent.getActivity(context, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        widget.setPendingIntentTemplate(R.id.temperatures, clickPI);

        // Create an Intent to launch ConfigurationActivity
        Intent intent = new Intent(context, LoginActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
        widget.setOnClickPendingIntent(R.id.configure, pendingIntent);

        widget.setOnClickPendingIntent(R.id.update_list, myUpdateIntent(context));
/*
        long interval = 3 * 60 * 1000;
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarm.setRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(), interval, myUpdateIntent(context));
        Log.e("app widget id - ", "alarm started");
*/
        setLastUpdateTime(widget);

        appWidgetManager.updateAppWidget(appWidgetIds, widget);

        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        final String action = (intent != null ? intent.getAction() : null);
        if (UPDATE_LIST.equals(action)) {
            Log.d("WidgetProvider", "onReceive(UPDATE_LIST)");
            if (isScreenOn(context)) {
                updateWidget(context);
            }
        }
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);

        turnAlarmOnOff(context, false);
        context.stopService(new Intent(context, ScreenMonitorService.class));
    }

    private boolean isScreenOn(Context context) {
        PowerManager mgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return (mgr != null) && mgr.isScreenOn();
    }

    private void updateWidget(Context context) {
        Log.d("WidgetProvider", "updateWidget()");

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int appWidgetIds[] = appWidgetManager.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.temperatures);

        RemoteViews widget = new RemoteViews(context.getPackageName(), R.layout.widget);
        setLastUpdateTime(widget);
        appWidgetManager.updateAppWidget(appWidgetIds, widget);
    }

    private void setLastUpdateTime(RemoteViews widget) {
        widget.setTextViewText(R.id.last_update, "Teploty v " + new SimpleDateFormat("HH:mm").format(new Date()));
    }
}