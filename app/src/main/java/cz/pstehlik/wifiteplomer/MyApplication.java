package cz.pstehlik.wifiteplomer;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.util.Log;

public class MyApplication extends Application {

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // called when screen orientation changes - widget must be updated, otherwise it'll stop responding
        Log.d("MyApplication", "OnConfigurationChanged!");

        // create intent to update all instances of the widget
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, this, WidgetProvider.class);

        // retrieve all appWidgetIds for the widget & put it into the Intent
        AppWidgetManager appWidgetMgr = AppWidgetManager.getInstance(this);
        ComponentName cm = new ComponentName(this, WidgetProvider.class);
        int[] appWidgetIds = appWidgetMgr.getAppWidgetIds(cm);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);

        // update the widget
        sendBroadcast(intent);
    }
}
