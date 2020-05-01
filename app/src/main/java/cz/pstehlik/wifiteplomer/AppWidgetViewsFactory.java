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
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;

public class AppWidgetViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private Context context;
    private int appWidgetId;

    private ArrayList<DataEntry> arrayList = new ArrayList<>();

    @Override
    public RemoteViews getViewAt(int position) {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.row);

        if (position >= 0 && position < arrayList.size()) {
            DataEntry d = arrayList.get(position);
            row.setTextViewText(android.R.id.text1, d.name);
            row.setTextViewText(android.R.id.text2, d.value);
            Intent fillInIntent = new Intent()
                    .putExtra("EXTRA_SABAKA_NODE", d.node)
                    .putExtra("EXTRA_SABAKA_SENSOR", d.name)
                    .putExtra("EXTRA_SABAKA_UNIT", d.unit);
            row.setOnClickFillInIntent(android.R.id.text2, fillInIntent);
        }

        // required for the clickIntent in AppWidgetViewsFactory.java to work
        Intent i = new Intent(Intent.ACTION_VIEW);
        row.setOnClickFillInIntent(android.R.id.text1, i);

        return row;
    }

    public AppWidgetViewsFactory(Context ctxt, Intent intent) {
        this.context = ctxt;
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    static public String getTempData(Context context) {
        Log.d("AppWidgetViewsFactory", "getTempData()");
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
            Log.e("WiFi Teploměr", "getTempData exception");
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

    private DataEntry getDataEntry(String node, JSONObject sensor) {
        try {
            String name = sensor.getString("n");
            double value = sensor.getDouble("v");
            String unit = sensor.getString("u");
            int range = sensor.getInt("r");

            String form = (unit.equals("ppm") || unit.equals("imp") || unit.length() == 0) ? "%.0f %s" : "%.1f %s";
            if (unit.length() == 0) unit = context.getResources().getString(value > 0 ? R.string.value_on : R.string.value_off);
            SpannableString s = new SpannableString(String.format(form, value, unit));
            if (range != 0) {
                int len = s.length() - unit.length() - 1;
                s.setSpan(new StyleSpan(Typeface.BOLD), 0, len, 0);
                s.setSpan(new ForegroundColorSpan((range > 0) ? Color.RED : Color.BLUE), 0, len, 0);
            }
            return new DataEntry(node, name, s, unit);
        } catch (JSONException e) {
            Log.e(getClass().getSimpleName(), "decode JSON exception");
        }
        return new DataEntry();
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
        ArrayList<JSONObject> list = new ArrayList<>();
        arrayList.clear();
        try {
            JSONObject reader = new JSONObject(json);
            JSONObject cidla = reader.getJSONObject("cidla");
            Iterator<?> nodes = cidla.keys();
            while (nodes.hasNext()) {
                String node = (String) nodes.next();
                JSONArray data = cidla.getJSONArray(node);
                list.clear();
                for (int i = 0; i < data.length(); i++) {
                    list.add(data.getJSONObject(i));
                }

                for (int position = 0; position < list.size(); position++) {
                    DataEntry x = getDataEntry(node, list.get(position));
                    SpannableStringBuilder s = new SpannableStringBuilder();
                    s.append(x.value);
                    int pos = position + 1;
                    while (pos < list.size()) {
                        DataEntry y = getDataEntry(node, list.get(pos));
                        if (x.name.equals(y.name)) {
                            s.append(' ');
                            s.append(y.value);
                            list.remove(pos);
                        } else pos++;
                    }

                    arrayList.add(new DataEntry(node, x.name, SpannableString.valueOf(s), x.unit));
                }
            }
        } catch (JSONException e) {
            Log.e(getClass().getSimpleName(), "decode JSON exception: " + json);
        }
    }

    private class DataEntry {
        public String node;
        public String name;
        public SpannableString value;
        public String unit;

        DataEntry() {
        }

        DataEntry(String _node, String _name, SpannableString _value, String _unit) {
            node = _node;
            name = _name;
            value = _value;
            unit = _unit;
        }
    }
}