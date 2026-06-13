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
import android.view.View;
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
import java.util.List;
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
    private static final String[] INT_UNITS = {"ppm", "ppb", "imp", "dBm", "s", "\u00B0"};

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
                float size = Math.round(14 * getFontScale(fontsize));
                row.setTextViewTextSize(android.R.id.text1, TypedValue.COMPLEX_UNIT_SP, size);
                row.setTextViewTextSize(android.R.id.text2, TypedValue.COMPLEX_UNIT_SP, size);
                row.setTextViewTextSize(R.id.trendArrow, TypedValue.COMPLEX_UNIT_SP, size);
            }
            row.setTextViewText(android.R.id.text1, d.name);
            if (d.id.isEmpty()) {
                row.setViewVisibility(R.id.switchIcon, View.GONE);
                row.setViewVisibility(android.R.id.text2, View.GONE);
            } else {
                String valStr = d.value.toString();
                String onStr = context.getString(R.string.value_on);
                String offStr = context.getString(R.string.value_off);
                if (valStr.equals(onStr) || valStr.equals(offStr)) {
                    row.setViewVisibility(R.id.switchIcon, View.VISIBLE);
                    row.setViewVisibility(android.R.id.text2, View.GONE);
                    row.setImageViewResource(R.id.switchIcon, getSwitchIcon(d.id, valStr.equals(onStr)));
                    if (fontsize >= 0) {
                        int iconSize = Math.round(16 * getFontScale(fontsize));
                        row.setBoolean(R.id.switchIcon, "setAdjustViewBounds", true);
                        row.setViewLayoutHeight(R.id.switchIcon, iconSize, TypedValue.COMPLEX_UNIT_DIP);
                    }
                    row.setViewVisibility(R.id.trendArrow,
                        d.trend.isEmpty() ? View.GONE : View.VISIBLE);
                    if (!d.trend.isEmpty())
                        row.setTextViewText(R.id.trendArrow, d.trend);
                    Intent fillInIntent = new Intent().putExtra("EXTRA_SABAKA_SENSOR", d.id);
                    row.setOnClickFillInIntent(R.id.switchIcon, fillInIntent);
                } else {
                    row.setViewVisibility(R.id.switchIcon, View.GONE);
                    row.setViewVisibility(android.R.id.text2, View.VISIBLE);
                    row.setViewVisibility(R.id.trendArrow, View.GONE);
                    row.setTextViewText(android.R.id.text2, d.value);
                    Intent fillInIntent = new Intent().putExtra("EXTRA_SABAKA_SENSOR", d.id);
                    row.setOnClickFillInIntent(android.R.id.text2, fillInIntent);
                }
            }
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

    // HTTP request with retry logic for post-wakeup scenarios
    private static String performHttpRequestWithRetry(String url) {
        int maxRetries = 3;
        int retryDelay = 1000; // 1 second

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            Log.d(TAG, "HTTP request attempt " + attempt + "/" + maxRetries);

            String result = performHttpRequest(url);
            if (result != null) {
                return result;
            }

            // If not the last attempt, wait before retry
            if (attempt < maxRetries) {
                try {
                    Thread.sleep(retryDelay);
                    retryDelay *= 2; // Exponential backoff
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        Log.w(TAG, "All HTTP request attempts failed");
        return null;
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
        SharedPreferences teplotyPrefs = context.getSharedPreferences(getWidgetPrefsName(appWidgetId), 0);
        Log.d(TAG, String.format("getTeplotyInfoUrl for id %d, prefs = %s", appWidgetId, teplotyPrefs));
        String login = teplotyPrefs.getString("login", "");
        if (login.isEmpty()) {
            // fallback to settings from version 1.x
            teplotyPrefs = context.getSharedPreferences("TeplotyPrefs", 0);
            login = teplotyPrefs.getString("login", "");
        }
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

            String arrow = teplotyPrefs.getBoolean("show_trend", true) ? computeTrend(context, id, value, unit) : "";

            if (unit.isEmpty()) {
                unit = context.getResources().getString(value > 0 ? R.string.value_on : R.string.value_off);
                return new DataEntry(node, id, name, new SpannableString(unit), unit, arrow);
            }
            else {
                String formatted = formatValue(value, unit);
                SpannableStringBuilder ssb = new SpannableStringBuilder(formatted + (arrow.isEmpty() ? "" : " " + arrow));
                if (range != 0) {
                    int end = formatted.length();
                    ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, end, 0);
                    ssb.setSpan(new ForegroundColorSpan((range > 0) ? Color.RED : Color.BLUE), 0, end, 0);
                }
                return new DataEntry(node, id, name, SpannableString.valueOf(ssb), unit, arrow);
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
        return false;
    }

    @Override
    public void onDataSetChanged() {
        Log.d(TAG, "onDataSetChanged for " + appWidgetId);
        getTemperatures();
    }

    private static ArrayList<DataEntry> createErrorList(String message) {
        ArrayList<DataEntry> list = new ArrayList<>();
        list.add(new DataEntry("", "", message, new SpannableString(message), ""));
        return list;
    }

    // this method uses intentionally synchronous HTTP downloading so that the widget doesn't start updating before fresh data is finished fetching
    // widget updating happens in a background thread by design anyway so Android is OK with this being synchronous
    private void getTemperatures() {
        String url = getTeplotyInfoUrl("data2.php", context, appWidgetId);
        Log.d(TAG, "Sync URL = " + url);
        if (url.isEmpty()) {
            instanceData.put(appWidgetId, createErrorList(context.getString(R.string.no_credentials)));
            return;
        }
        List<String> selectedSensors = SelectSensorActivity.loadSelectedSensors(context.getSharedPreferences(getWidgetPrefsName(appWidgetId), 0));
        String jsonResult = performHttpRequestWithRetry(url);
        if (jsonResult != null) {
            processJsonData(jsonResult, selectedSensors);
        } else {
            instanceData.put(appWidgetId, createErrorList(context.getString(R.string.error)));
        }
    }

    private void processJsonData(String json, List<String> selectedSensors) {
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
                    if (!selectedSensors.isEmpty() && !selectedSensors.contains(x.id)) continue;     // skip sensor if it's not selected
                    SpannableStringBuilder s = new SpannableStringBuilder();
                    s.append(x.value);
                    // merge multiple sensors of the same name on one line
                    int pos = position + 1;
                    while (pos < list.size()) {
                        DataEntry y = getDataEntry(node, list.get(pos));
                        if (x.name.equals(y.name)) {
                            s.append(' ');
                            s.append(y.value);
                            list.remove(pos);
                        } else pos++;
                    }

                    currentData.add(new DataEntry(node, x.id, x.name, SpannableString.valueOf(s), x.unit, x.trend));
                }
            }
            instanceData.put(appWidgetId, currentData);
            Log.d(TAG, "freshly downloaded data for ID = " + appWidgetId);
        } catch (JSONException e) {
            instanceData.put(appWidgetId, new ArrayList<>());
            Log.e(TAG, "decode JSON exception: " + e.getMessage());
        }
    }

    public static float getFontScale(int fontsize) {
        return (float) Math.pow(1.142857143, fontsize);
    }

    static int getSwitchIcon(String id, boolean isOn) {
        boolean relay = id.length() >= 2 && id.charAt(0) == 'f' && id.charAt(1) >= '1' && id.charAt(1) <= '8';
        if (relay) return isOn ? R.drawable.ic_relay_on : R.drawable.ic_relay_off;
        return isOn ? R.drawable.ic_switch_on : R.drawable.ic_switch_off;
    }

    static String computeTrend(Context ctx, String sensorId, double value, String unit) {
        SharedPreferences prefs = ctx.getSharedPreferences("TrendPrefs", 0);
        String json = prefs.getString(sensorId, "[]");
        JSONArray arr;
        long now = System.currentTimeMillis();
        try {
            arr = new JSONArray(json);
            long lastTs = arr.length() > 0 ? arr.getJSONArray(arr.length() - 1).getLong(1) : -1;
            if (lastTs < 0 || now - lastTs >= 50000) {
                arr.put(new JSONArray().put(value).put(now));
                if (arr.length() > 5) arr.remove(0);
                prefs.edit().putString(sensorId, arr.toString()).apply();
            }
        } catch (JSONException e) { return ""; }
        long cutoff = now - 1_800_000;
        while (arr.length() > 0) {
            try { if (arr.getJSONArray(0).getLong(1) >= cutoff) break; } catch (JSONException e) { /* old-format entry, will be removed */ }
            arr.remove(0);
        }
        int n = arr.length();
        if (n < 2) return "";
        long t0;
        try { t0 = arr.getJSONArray(0).getLong(1); } catch (JSONException e) { return ""; }
        double lambda = 0.15, sumW = 0, sumWX = 0, sumWY = 0, sumWXY = 0, sumWX2 = 0;
        for (int i = 0; i < n; i++) {
            try {
                JSONArray pair = arr.getJSONArray(i);
                double v = pair.getDouble(0);
                double x = (pair.getLong(1) - t0) / 60000.0; // minutes since first
                double w = Math.exp(-lambda * x);
                sumW += w; sumWX += w * x; sumWY += w * v;
                sumWXY += w * x * v; sumWX2 += w * x * x;
            } catch (JSONException e) { return ""; }
        }
        double denom = sumW * sumWX2 - sumWX * sumWX;
        if (denom == 0) return "\u2192 ";
        double slope = (sumW * sumWXY - sumWX * sumWY) / denom;
        double threshold = Arrays.asList(INT_UNITS).contains(unit) ? 1.0 : 0.06;
        if (slope > threshold) return "\u2197";
        if (slope < -threshold) return "\u2198";
        return "\u2192";
    }

    static String formatValue(double value, String unit) {
        String fmt = Arrays.asList(INT_UNITS).contains(unit) ? "%.0f %s" : "%.1f %s";
        return String.format(fmt, value, unit);
    }

    private static class DataEntry {
        public String node;
        public String id;
        public String name;
        public SpannableString value;
        public String unit;
        public String trend;

        DataEntry() { trend = ""; }

        DataEntry(String _node, String _id, String _name, SpannableString _value, String _unit) {
            this(_node, _id, _name, _value, _unit, "");
        }

        DataEntry(String _node, String _id, String _name, SpannableString _value, String _unit, String _trend) {
            node = _node; id = _id; name = _name; value = _value; unit = _unit; trend = _trend;
        }
    }
}