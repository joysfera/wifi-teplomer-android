package cz.pstehlik.wifiteplomer;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

public class AppWidgetViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private static final String TAG = "AppWidgetViewsFactory";
    private final Context context;
    private final int appWidgetId;
    private final SharedPreferences teplotyPrefs;
    private static final java.util.Map<Integer, ArrayList<DataEntry>> instanceData = new java.util.concurrent.ConcurrentHashMap<>();
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static String getWidgetPrefsName(int appWidgetId) { return "TeplotyPrefs_" + appWidgetId; }

    public AppWidgetViewsFactory(Context ctxt, Intent intent) {
        this.context = ctxt;
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        teplotyPrefs = context.getSharedPreferences(getWidgetPrefsName(appWidgetId), 0);
        Log.d(TAG, String.format("ctor for id %d, prefs = %s", appWidgetId, teplotyPrefs));
    }

    @Override
    public RemoteViews getViewAt(int position) {
        // Use instance-specific data
        ArrayList<DataEntry> currentData = instanceData.get(appWidgetId);
        if (currentData == null) currentData = new ArrayList<>();

        int wid = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES ? R.layout.row_night : R.layout.row;

        RemoteViews row = new RemoteViews(context.getPackageName(), wid);

        if (position >= 0 && position < currentData.size()) {
            DataEntry d = currentData.get(position);
            final int fontsize = (teplotyPrefs != null) ? teplotyPrefs.getInt("fontsize", -1) : -1;
            if (fontsize >= 0) {
                float size = Math.round(14 * (float)Math.pow(1.142857143, fontsize));
                row.setTextViewTextSize(android.R.id.text1, TypedValue.COMPLEX_UNIT_SP, size);
                row.setTextViewTextSize(android.R.id.text2, TypedValue.COMPLEX_UNIT_SP, size);
            }
            row.setTextViewText(android.R.id.text1, d.name);
            row.setTextViewText(android.R.id.text2, d.value);
            Intent fillInIntent = new Intent().putExtra("EXTRA_SABAKA_SENSOR", d.id);
            row.setOnClickFillInIntent(android.R.id.text2, fillInIntent);
        }

        // required for the clickIntent in AppWidgetViewsFactory.java to work
        Intent i = new Intent(Intent.ACTION_VIEW);
        row.setOnClickFillInIntent(android.R.id.text1, i);

        return row;
    }

    public interface TempDataCallback {
        void onResult(String data);
        void onError(Exception e);
    }

    static public void getTempData(Context context, int appWidgetId, TempDataCallback callback) {
        Log.d(TAG, "getTempData(" + appWidgetId + ")");
        String url = getTeplotyInfoUrl("data2.php", context, appWidgetId);
        Log.d(TAG, "URL = " + url);
        if (url.isEmpty()) {
            callback.onResult("");
            return;
        }
        executorService.execute(() -> {
            String result = performHttpRequest(url);
            if (result != null) {
                callback.onResult(result);
            } else {
                callback.onError(new IOException("Failed to fetch data"));
            }
        });
    }

    private static String performHttpRequest(String url) {
        HttpsURLConnection urlConnection = null;
        StringBuilder json = new StringBuilder();
        try {
            urlConnection = (HttpsURLConnection) new URL(url).openConnection();
            urlConnection.setConnectTimeout(5000);  // 5 seconds
            urlConnection.setReadTimeout(10000);    // 10 seconds

            int status = urlConnection.getResponseCode();
            if (status == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        json.append(line);
                    }
                }
                return json.toString();
            }
        } catch (IOException e) {
            Log.e(TAG, "HTTP request exception: " + e.getMessage());
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return null;
    }

    public static void shutdown() {
        executorService.shutdown();
    }

    static public String getTeplotyInfoUrl(String page, Context context, int appWidgetId) {
        final SharedPreferences teplotyPrefs = context.getSharedPreferences(getWidgetPrefsName(appWidgetId), 0);
        Log.d(TAG, String.format("getTeplotyInfoUrl for id %d, prefs = %s", appWidgetId, teplotyPrefs));
        final String login = teplotyPrefs.getString("login", "");
        final String pwd = teplotyPrefs.getString("pwd", "");
        if (login.isEmpty()) return "";
        try {
            return String.format("https://teploty.info/%s?login=%s&pwd=%s", page, URLEncoder.encode(login, "UTF-8"), URLEncoder.encode(pwd, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        instanceData.put(appWidgetId, new ArrayList<>());
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        instanceData.remove(appWidgetId);
    }

    @Override
    public int getCount() {
        Log.d(TAG, "getCount(" + appWidgetId +")");
        ArrayList<DataEntry> currentData = instanceData.get(appWidgetId);
        if (currentData == null) return 0;
        return currentData.size();
    }

    private DataEntry getDataEntry(String node, JSONObject sensor) {
        try {
            String id = sensor.getString("i");
            String name = sensor.getString("n");
            double value = sensor.getDouble("v");
            String unit = sensor.getString("u");
            int range = sensor.getInt("r");

            if (unit.isEmpty()) {
                unit = context.getResources().getString(value > 0 ? R.string.value_on : R.string.value_off);
                return new DataEntry(node, id, name, new SpannableString(unit), unit);
            }
            else {
                final String[] units = {"ppm", "ppb", "imp", "dBm", "s", "°"};
                String form = Arrays.asList(units).contains(unit) ? "%.0f %s" : "%.1f %s";
                SpannableString s = new SpannableString(String.format(form, value, unit));
                if (range != 0) {
                    int len = s.length() - unit.length() - 1;
                    s.setSpan(new StyleSpan(Typeface.BOLD), 0, len, 0);
                    s.setSpan(new ForegroundColorSpan((range > 0) ? Color.RED : Color.BLUE), 0, len, 0);
                }
                return new DataEntry(node, id, name, s, unit);
            }
        } catch (JSONException e) {
            Log.e(TAG, "decode JSON exception: " + e.getMessage());
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
        Log.d(TAG, "onDataSetChanged for " + appWidgetId);
        getTemperatures();
    }

    // this method uses intentionally synchronous HTTP downloading so that the widget doesn't start updating before fresh data is finished fetching
    // widget updating happens in a background thread by design anyway so Android is OK with this being synchronous
    private void getTemperatures() {
        String url = getTeplotyInfoUrl("data2.php", context, appWidgetId);
        Log.d(TAG, "Sync URL = " + url);
        if (url.isEmpty()) {
            instanceData.put(appWidgetId, new ArrayList<>());
            return;
        }

        String jsonResult = performHttpRequest(url);
        if (jsonResult != null) {
            processJsonData(jsonResult);
        } else {
            instanceData.put(appWidgetId, new ArrayList<>());
        }
    }

    private void processJsonData(String json) {
        if (json.isEmpty()) {
            instanceData.put(appWidgetId, new ArrayList<>());
            return;
        }

        ArrayList<JSONObject> list = new ArrayList<>();
        ArrayList<DataEntry> currentData = new ArrayList<>();
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

                    currentData.add(new DataEntry(node, x.id, x.name, SpannableString.valueOf(s), x.unit));
                }
            }
            instanceData.put(appWidgetId, currentData);
            Log.d(TAG, "freshly downloaded data for ID = " + appWidgetId);
        } catch (JSONException e) {
            instanceData.put(appWidgetId, new ArrayList<>());
            Log.e(TAG, "decode JSON exception: " + e.getMessage());
        }
    }

    private static class DataEntry {
        public String node;
        public String id;
        public String name;
        public SpannableString value;
        public String unit;

        DataEntry() {
        }

        DataEntry(String _node, String _id, String _name, SpannableString _value, String _unit) {
            node = _node;
            id = _id;
            name = _name;
            value = _value;
            unit = _unit;
        }
    }
}