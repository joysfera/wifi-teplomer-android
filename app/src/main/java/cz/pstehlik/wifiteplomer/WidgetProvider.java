package cz.pstehlik.wifiteplomer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;

public class WidgetProvider extends AppWidgetProvider {
    public static String UPDATE_LIST = "UPDATE_LIST";
    static long lastForcedUpdateAt = 0;

    public static boolean turnAlarmOnOff(Context context, boolean turnOn) {
        boolean updated = false;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return updated;
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
                    updated = true;
                } catch (PendingIntent.CanceledException e) {
                    Log.wtf("WidgetProvider", "Exception in pendingIntent.send()");
                }
            }
        } else {
            alarmManager.cancel(pendingIntent);
            Log.d("WidgetProvider", "Alarm disabled");
        }
        return updated;
    }

    private static PendingIntent myUpdateIntent(Context context) {
        Intent in = new Intent(context, WidgetProvider.class);
        in.setAction(UPDATE_LIST);
        Log.d("WidgetProvider", "myUpdateIntent generated to refresh widget in timely manner");
        return PendingIntent.getBroadcast(context, 0, in, PendingIntent.FLAG_IMMUTABLE);
    }

    private void updateClickIntents(Context context, RemoteViews widget) {
        Intent svcIntent = new Intent(context, WidgetService.class);
        //svcIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
        //svcIntent.setData(Uri.parse(svcIntent .toUri(Intent.URI_INTENT_SCHEME)));

        widget.setRemoteAdapter(R.id.temperatures, svcIntent);

        Intent clickIntent = new Intent(context, WidgetProvider.class).setAction("SABAKA_KLIK");
        PendingIntent clickPI = PendingIntent.getBroadcast(context, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        widget.setPendingIntentTemplate(R.id.temperatures, clickPI);

        // Create an Intent to launch ConfigurationActivity
        Intent intent = new Intent(context, LoginActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        widget.setOnClickPendingIntent(R.id.configure, pendingIntent);

        // Create an Intent to force updating widget
        widget.setOnClickPendingIntent(R.id.update_list, myUpdateIntent(context));

        // update time
        widget.setTextViewText(R.id.last_update, context.getString(R.string.values_at) + new SimpleDateFormat(" HH:mm").format(new Date()));
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

        Log.d("WidgetProvider", "onUpdate(" + appWidgetIds.length + ") => widget added?");

        // this used to be in onEnabled() but that was not called everytime, unfortunately
        turnAlarmOnOff(context, true); // enable timer only if screen is on
        MyBroadcastReceiver.registerScreenReceiver(context);
        // end of what used to be in onEnabled()

        updateAllWidgets(context, appWidgetManager, appWidgetIds);

        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        final String action = (intent != null ? intent.getAction() : null);
        if (UPDATE_LIST.equals(action)) {
            Log.d("WidgetProvider", "onReceive(UPDATE_LIST)");
            if (isScreenOn(context)) {
                // something is calling the updateWidget() twice
                Log.d("WidgetProvider", "trying to force-enabling timer and re-registering screen intents - maybe unnecessarily?");
                boolean updated = turnAlarmOnOff(context, true); // try force-enabling the timer in case app was frozen by Android
                MyBroadcastReceiver.registerScreenReceiver(context); //re-register the screen intents because they tend to stop coming
                if (!updated)
                    updateWidget(context);
            }
        }
        else if ("SABAKA_KLIK".equals(action)) {
            String sensor = intent.getStringExtra("EXTRA_SABAKA_SENSOR");
            String url = AppWidgetViewsFactory.getTeplotyInfoUrl("profile.php", context);
            if (sensor != null && !sensor.isEmpty()) {
                url = AppWidgetViewsFactory.getTeplotyInfoUrl("graph.php", context) + "&sensor=" + sensor;
            }
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setData(Uri.parse(url));
            context.startActivity(i);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        Log.d("WidgetProvider", "AppWidgetOptionsChanged!");

        // See the dimensions and
        Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);

        // Get min width and height.
        int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
/*
        RemoteViews widget = new RemoteViews(context.getPackageName(), R.layout.widget);
        widget.setTextViewTextSize(R.id.last_update, TypedValue.COMPLEX_UNIT_SP, 32);
        widget.setTextViewTextSize(R.id.temperatures,TypedValue.COMPLEX_UNIT_SP, 24);
*/
        // Obtain appropriate widget and update it.
        ///appWidgetManager.updateAppWidget(appWidgetId, getRemoteViews(context, minWidth, minHeight));
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        Log.d("WidgetProvider", "onDisabled() => widget removed!");
        MyBroadcastReceiver.unregisterScreenReceiver(context);
        turnAlarmOnOff(context, false);
    }

    private boolean isScreenOn(Context context) {
        PowerManager mgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return (mgr != null) && mgr.isScreenOn();
    }

    private void updateAllWidgets(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        int wid = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES ? R.layout.widget_night : R.layout.widget;
        for(int i = 0; i < appWidgetIds.length; i++) {
            int appWidgetId = appWidgetIds[i];
            RemoteViews widget = new RemoteViews(context.getPackageName(), wid);
            updateClickIntents(context, widget);
            appWidgetManager.updateAppWidget(appWidgetId, widget);
        }
    }
    private void updateWidget(Context context) {
        Log.d("WidgetProvider", "updateWidget()");

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
        updateAllWidgets(context, appWidgetManager, appWidgetIds);
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.temperatures);
    }
}