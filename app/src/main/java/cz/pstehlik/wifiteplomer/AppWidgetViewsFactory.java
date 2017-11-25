package cz.pstehlik.wifiteplomer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;

public class AppWidgetViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private Context context = null;
    private int appWidgetId;
    private ArrayList<String> arrayList = new ArrayList<String>();

    public AppWidgetViewsFactory(Context ctxt, Intent intent) {
        this.context = ctxt;
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    static public String getTempData(Context context) {
        StringBuilder json = new StringBuilder();
        HttpsURLConnection urlConnection = null;
        try {
            URL url = new URL(getTeplotyInfoUrl("data.php", context));
            urlConnection = (HttpsURLConnection) url.openConnection();
            int status = urlConnection.getResponseCode();
            if (status == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        json.append(line);
                    }
                }
            }
        } catch (Exception e) {
        } finally {
            if (urlConnection != null) urlConnection.disconnect();
        }
        return json.toString();
    }

    static public String getTeplotyInfoUrl(String page, Context context) {
        final SharedPreferences teplotyPrefs = context.getSharedPreferences("TeplotyPrefs", 0);
        final String login = teplotyPrefs.getString("login", "");
        final String pwd = teplotyPrefs.getString("pwd", "");
        try {
            return String.format("https://teploty.info/%s?login=%s&pwd=%s", page, URLEncoder.encode(login, "UTF-8"), URLEncoder.encode(pwd, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    @Override
    public void onCreate() {
        arrayList.clear();
    }

    @Override
    public void onDestroy() {
        // no-op
    }

    @Override
    public int getCount() {
        return (arrayList.size());
    }

    @Override
    public RemoteViews getViewAt(int position) {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.row);

        String t = arrayList.get(position);
        String[] p = t.split("=");
        row.setTextViewText(android.R.id.text1, p[0]);
        row.setTextViewText(android.R.id.text2, p[1]);

        // required for the clickIntent in AppWidgetViewsFactory.java to work
        Intent i = new Intent(Intent.ACTION_VIEW);
        row.setOnClickFillInIntent(android.R.id.text1, i);

        return row;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public void onDataSetChanged() {
        getTemperatures();
    }

    private void getTemperatures() {
        String json = getTempData(context);
        arrayList.clear();
        try {
            JSONObject reader = new JSONObject(json);
            JSONObject cidla = reader.getJSONObject("cidla");
            Iterator<?> keys = cidla.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
                String val = cidla.get(key).toString();
                // workaround for missing units in the JSON data, will be removed after upgrading the JSON data
                String unit = "\u2103";
                if (val.contains("%")) {
                    val = val.replace("%", "");
                    unit = "%";
                }
                float num = Float.parseFloat(val);
                // end of the workaround
                arrayList.add(String.format("%s=%.1f %s", key, num, unit));
            }
        } catch (JSONException e) {
            Log.e(getClass().getSimpleName(), "decode JSON exception");
        }
    }
}