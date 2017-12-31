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
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;

public class AppWidgetViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private Context context = null;
    private int appWidgetId;
    private ArrayList<JSONObject> arrayList = new ArrayList<JSONObject>();

    public AppWidgetViewsFactory(Context ctxt, Intent intent) {
        this.context = ctxt;
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    static public String getTempData(Context context) {
        StringBuilder json = new StringBuilder();
        HttpsURLConnection urlConnection = null;
        try {
            URL url = new URL(getTeplotyInfoUrl("data2.php", context));
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

        try {
            JSONObject sensor = arrayList.get(position);
            String name = sensor.getString("n");
            double value = sensor.getDouble("v");
            String unit = sensor.getString("u");
            int range = sensor.getInt("r");
            row.setTextViewText(android.R.id.text1, name);
            SpannableString s = new SpannableString(String.format("%.1f %s", value, unit));
            if (range != 0)
                s.setSpan(new StyleSpan(Typeface.BOLD), 0, Math.max(s.length() - 2, 0), 0);
            row.setTextViewText(android.R.id.text2, s);
            row.setTextColor(android.R.id.text2, (range == 0) ? Color.BLACK : ((range > 0) ? Color.RED : Color.BLUE));
        } catch (JSONException e) {
            Log.e(getClass().getSimpleName(), "decode JSON exception");
        }

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
        String json = getTempData(context.getApplicationContext());
        arrayList.clear();
        try {
            JSONObject reader = new JSONObject(json);
            JSONObject cidla = reader.getJSONObject("cidla");
            Iterator<?> nodes = cidla.keys();
            while (nodes.hasNext()) {
                String node = (String) nodes.next();
                JSONArray data = cidla.getJSONArray(node);
                for (int i = 0; i < data.length(); i++) {
                    arrayList.add(data.getJSONObject(i));
                }
            }
        } catch (JSONException e) {
            Log.e(getClass().getSimpleName(), "decode JSON exception");
        }
    }
}