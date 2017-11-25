package cz.pstehlik.wifiteplomer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;

public class WidgetProvider extends AppWidgetProvider {
    public static String UPDATE_LIST = "UPDATE_LIST";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

        Log.e("app widget id - ", appWidgetIds.length + " = onUpdate");
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

        clickIntent = new Intent(context, WidgetProvider.class);
        clickIntent.setAction(UPDATE_LIST);
        PendingIntent pendingIntentRefresh = PendingIntent.getBroadcast(context, 0, clickIntent, 0);
        widget.setOnClickPendingIntent(R.id.update_list, pendingIntentRefresh);

        long interval = 60000;
        Intent in = new Intent(context, WidgetProvider.class);
        in.setAction(UPDATE_LIST);
        PendingIntent alarmPI = PendingIntent.getBroadcast(context, 0, in, 0);
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarm.setRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(), interval, alarmPI);

        setLastUpdateTime(widget);

        appWidgetManager.updateAppWidget(appWidgetIds, widget);

        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent.getAction().equalsIgnoreCase(UPDATE_LIST)) {
            updateWidget(context);
        }
    }

    private void updateWidget(Context context) {
        Log.e("app widget id - ", "updating widget");

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