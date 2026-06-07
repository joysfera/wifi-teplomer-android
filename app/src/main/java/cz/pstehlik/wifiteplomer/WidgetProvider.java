package cz.pstehlik.wifiteplomer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class WidgetProvider extends AppWidgetProvider {
    public static final String UPDATE_LIST = "UPDATE_LIST";

    public static void turnAlarmOnOff(Context context, boolean turnOn) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        PendingIntent pendingIntent = myUpdateIntentAll(context);
        Log.d("WidgetProvider", "turnAlarmOnOff(" + turnOn + ")");

        if (turnOn) {
            long interval = 3 * 60 * 1000;
            alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + interval, interval, pendingIntent);
            Log.d("WidgetProvider", "Alarm set");
        } else {
            alarmManager.cancel(pendingIntent);
            Log.d("WidgetProvider", "Alarm disabled");
        }
    }

    public static void requestWidgetUpdate(Context context) {
        Intent in = new Intent(context, WidgetProvider.class);
        in.setAction(UPDATE_LIST);
        context.sendBroadcast(in);
    }

    private static PendingIntent myUpdateIntent(Context context, int appWidgetId) {
        Intent in = new Intent(context, WidgetProvider.class);
        in.setAction(UPDATE_LIST);
        in.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        Log.d("WidgetProvider", String.format("myUpdateIntent generated to refresh widget %d in timely manner", appWidgetId));
        return PendingIntent.getBroadcast(context, appWidgetId, in, PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent myUpdateIntentAll(Context context) {
        Intent in = new Intent(context, WidgetProvider.class);
        in.setAction(UPDATE_LIST);
        Log.d("WidgetProvider", String.format("myUpdateIntentAll generated to refresh ALL widgets"));
        return PendingIntent.getBroadcast(context, 0, in, PendingIntent.FLAG_IMMUTABLE);
    }

    private void updateClickIntents(Context context, RemoteViews widget, int appWidgetId) {
        Intent svcIntent = new Intent(context, WidgetService.class);
        svcIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        svcIntent.setData(Uri.parse(svcIntent.toUri(Intent.URI_INTENT_SCHEME)));

        widget.setRemoteAdapter(R.id.temperatures, svcIntent);

        Intent clickIntent = new Intent(context, WidgetProvider.class).setAction("SABAKA_KLIK");
        clickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent clickPI = PendingIntent.getBroadcast(context, appWidgetId, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        widget.setPendingIntentTemplate(R.id.temperatures, clickPI);

        // Create an Intent to launch ConfigurationActivity (new task so back goes to home)
        Intent intent = new Intent(context, LoginActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, PendingIntent.FLAG_IMMUTABLE);
        widget.setOnClickPendingIntent(R.id.configure, pendingIntent);

        // Create an Intent to force updating widget
        widget.setOnClickPendingIntent(R.id.update_list, myUpdateIntent(context, appWidgetId));

        // update time
        widget.setTextViewText(R.id.last_update, context.getString(R.string.values_at) + new SimpleDateFormat(" HH:mm").format(new Date()));
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        Log.d("WidgetProvider", "onEnabled() => first widget instance added!");
/*
        // Enable timer and register screen receiver ONLY when the first widget is enabled
        turnAlarmOnOff(context, true);
        MyBroadcastReceiver.registerScreenReceiver(context);
 */
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

        Log.d("WidgetProvider", "onUpdate(" + appWidgetIds.length + ") => widget added?");
        // this used to be in onEnabled() but that was not called everytime, unfortunately
        turnAlarmOnOff(context, true); // enable timer only if screen is on
        MyBroadcastReceiver.registerScreenReceiver(context);
        // end of what used to be in onEnabled()
        updateListOfWidgets(context, appWidgetManager, appWidgetIds);

        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        final String action = (intent != null ? intent.getAction() : null);
        if (UPDATE_LIST.equals(action)) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            Log.d("WidgetProvider", "onReceive(UPDATE_LIST, " + appWidgetId +")");
            if (isScreenOn(context)) {
                turnAlarmOnOff(context, true);
                MyBroadcastReceiver.registerScreenReceiver(context);
                updateWidget(context, appWidgetId);
            }
            else {
                Log.d("WidgetProvider", "Screen is OFF so no updating necessary");
            }
        }
        else if ("SABAKA_KLIK".equals(action)) {
            String sensor = intent.getStringExtra("EXTRA_SABAKA_SENSOR");
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            String url = AppWidgetViewsFactory.getTeplotyInfoUrl("profile.php", context, appWidgetId);
            if (sensor != null && !sensor.isEmpty()) {
                url = AppWidgetViewsFactory.getTeplotyInfoUrl("graph.php", context, appWidgetId) + "&sensor=" + sensor;
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
/*
        // See the dimensions and
        Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);

        // Get min width and height.
        int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);

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
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        Log.d("WidgetProvider", "onDeleted called for " + Arrays.toString(appWidgetIds));

        for (int appWidgetId : appWidgetIds) {
            // Clean up preferences for this widget ID
            SharedPreferences prefs = context.getSharedPreferences(AppWidgetViewsFactory.getWidgetPrefsName(appWidgetId), 0);
            prefs.edit().clear().apply();
            Log.d("WidgetProvider", "Deleted prefs for widget id " + appWidgetId);
        }

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisWidget = new ComponentName(context, WidgetProvider.class);
        int[] remainingWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

        if (remainingWidgetIds.length == 0) {
            Log.d("WidgetProvider", "Turning off alarm because this was the last instance");
            turnAlarmOnOff(context, false);
            MyBroadcastReceiver.unregisterScreenReceiver(context);
        }
    }

    private boolean isScreenOn(Context context) {
        PowerManager mgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return (mgr != null) && mgr.isScreenOn();
    }

    private void updateListOfWidgets(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        int wid = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES ? R.layout.widget_night : R.layout.widget;
        Log.d("WidgetProvider", "updateListOfWidgets(" + Arrays.toString(appWidgetIds) +")");
        for (int appWidgetId : appWidgetIds) {
            RemoteViews widget = new RemoteViews(context.getPackageName(), wid);
            updateClickIntents(context, widget, appWidgetId);
            appWidgetManager.updateAppWidget(appWidgetId, widget);
        }
    }
    private void updateWidget(Context context, int appWidgetId) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds;
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            appWidgetIds = new int[]{appWidgetId};
        }
        else {
            ComponentName thisWidget = new ComponentName(context, WidgetProvider.class);
            appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
        }
        Log.d("WidgetProvider", "updateWidget(" + appWidgetId + ")");
        updateListOfWidgets(context, appWidgetManager, appWidgetIds);
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.temperatures);
    }
}