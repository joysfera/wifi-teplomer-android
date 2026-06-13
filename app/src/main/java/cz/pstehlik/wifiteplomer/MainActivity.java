package cz.pstehlik.wifiteplomer;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String SHARED_PREFS = "TeplotyPrefs";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout sensorContainer;
    private View noCredentials;
    private ScrollView scroll;
    private ProgressBar loading;
    private ProgressBar refreshProgress;
    private long lastUpdateTime = -1;
    private boolean hasCredentials;

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refreshData();
            handler.postDelayed(this, 60000);
        }
    };

    private final Runnable countdownTask = new Runnable() {
        @Override
        public void run() {
            if (lastUpdateTime > 0) {
                long elapsed = System.currentTimeMillis() - lastUpdateTime;
                int remaining = Math.max(0, 60 - (int)(elapsed / 1000));
                refreshProgress.setProgress(remaining);
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        sensorContainer = findViewById(R.id.sensor_container);
        noCredentials = findViewById(R.id.no_credentials);
        scroll = findViewById(R.id.scroll);
        loading = findViewById(R.id.loading);
        refreshProgress = findViewById(R.id.refresh_progress);
        refreshProgress.setOnClickListener(v -> {
            refreshProgress.setProgress(60);
            refreshData();
        });

        Button cfgBtn = findViewById(R.id.configure_btn);
        cfgBtn.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));

        migrateCredentials();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, LoginActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void migrateCredentials() {
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS, 0);
        if (!prefs.getString("login", "").isEmpty()) return;
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        for (int id : mgr.getAppWidgetIds(new ComponentName(this, WidgetProvider.class))) {
            SharedPreferences wp = getSharedPreferences(AppWidgetViewsFactory.getWidgetPrefsName(id), 0);
            String login = wp.getString("login", "");
            if (!login.isEmpty()) {
                prefs.edit()
                    .putString("login", login)
                    .putString("pwd", wp.getString("pwd", ""))
                    .apply();
                break;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hasCredentials = !getSharedPreferences(SHARED_PREFS, 0).getString("login", "").isEmpty();
        if (hasCredentials) {
            noCredentials.setVisibility(View.GONE);
            scroll.setVisibility(View.VISIBLE);
            refreshProgress.setVisibility(View.VISIBLE);
            handler.post(refreshTask);
            handler.post(countdownTask);
            if (lastUpdateTime > 0)
                getSupportActionBar().setTitle(getString(R.string.values_at) + new SimpleDateFormat(" HH:mm", Locale.getDefault()).format(new Date(lastUpdateTime)));
        } else {
            noCredentials.setVisibility(View.VISIBLE);
            scroll.setVisibility(View.GONE);
            refreshProgress.setVisibility(View.GONE);
            ((TextView) findViewById(R.id.no_cred_text)).setText(R.string.no_credentials);
            ((Button) findViewById(R.id.configure_btn)).setText(R.string.setup_login);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshTask);
        handler.removeCallbacks(countdownTask);
    }

    private void refreshData() {
        loading.setVisibility(View.VISIBLE);
        AppWidgetViewsFactory.getTempData(this, 0, new AppWidgetViewsFactory.TempDataCallback() {
            @Override
            public void onResult(String data) {
                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    if (data != null && data.startsWith("{\"cidla")) {
                        parseAndDisplay(data);
                        lastUpdateTime = System.currentTimeMillis();
                        getSupportActionBar().setTitle(getString(R.string.values_at) + new SimpleDateFormat(" HH:mm", Locale.getDefault()).format(new Date(lastUpdateTime)));
                        refreshProgress.setProgress(60);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> loading.setVisibility(View.GONE));
                Log.e(TAG, "refresh: " + e.getMessage());
            }
        });
    }

    private void parseAndDisplay(String json) {
        sensorContainer.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS, 0);
        int fontsize = prefs.getInt("fontsize", -1);
        float textSize = -1;
        if (fontsize >= 0)
            textSize = Math.round(14 * AppWidgetViewsFactory.getFontScale(fontsize));
        List<String> selected = SelectSensorActivity.loadSelectedSensors(prefs);
        try {
            JSONObject root = new JSONObject(json);
            JSONObject cidla = root.getJSONObject("cidla");
            Iterator<?> nodes = cidla.keys();
            while (nodes.hasNext()) {
                String node = (String) nodes.next();
                JSONArray arr = cidla.getJSONArray(node);

                // collect visible sensors for this node
                ArrayList<JSONObject> visible = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.getJSONObject(i);
                    if (!selected.isEmpty() && !selected.contains(s.getString("i")))
                        continue;
                    visible.add(s);
                }
                if (visible.isEmpty()) continue;

                // node header
                TextView hdr = new TextView(this);
                hdr.setText(node);
                if (textSize > 0) hdr.setTextSize(textSize);
                hdr.setTypeface(null, 1);
                hdr.setPadding(8, 24, 8, 4);
                sensorContainer.addView(hdr);

                for (JSONObject s : visible) {
                    String id = s.getString("i");
                    String name = s.getString("n");
                    double val = s.getDouble("v");
                    String unit = s.getString("u");
                    int range = s.getInt("r");

                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setPadding(8, 8, 8, 8);

                    TextView nameTv = new TextView(this);
                    nameTv.setText(name);
                    nameTv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
                    if (textSize > 0) nameTv.setTextSize(textSize);
                    nameTv.setOnClickListener(v -> {
                        String url = AppWidgetViewsFactory.getTeplotyInfoUrl("profile.php", this, 0);
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    });

                    View.OnClickListener graphClick = v -> {
                        String url = AppWidgetViewsFactory.getTeplotyInfoUrl("graph.php", this, 0) + "&sensor=" + id;
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    };

                    String trend = prefs.getBoolean("show_trend", true) ? AppWidgetViewsFactory.computeTrend(this, id, val) : "";
                    if (unit.isEmpty()) {
                        ImageView valView = new ImageView(this);
                        valView.setImageResource(val > 0 ? R.drawable.ic_switch_on : R.drawable.ic_switch_off);
                        valView.setAdjustViewBounds(true);
                        if (textSize > 0) {
                            int iconDp = Math.round(16 * AppWidgetViewsFactory.getFontScale(fontsize));
                            valView.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, iconDp, getResources().getDisplayMetrics())));
                        }
                        valView.setOnClickListener(graphClick);
                        row.addView(nameTv);
                        if (!trend.isEmpty()) {
                            LinearLayout iconRow = new LinearLayout(this);
                            iconRow.setOrientation(LinearLayout.HORIZONTAL);
                            iconRow.addView(valView);
                            TextView arrowTv = new TextView(this);
                            arrowTv.setText(trend);
                            if (textSize > 0) arrowTv.setTextSize(textSize);
                            iconRow.addView(arrowTv);
                            row.addView(iconRow);
                        } else {
                            row.addView(valView);
                        }
                        sensorContainer.addView(row);
                        continue;
                    }

                    String valStr = AppWidgetViewsFactory.formatValue(val, unit) + (trend.isEmpty() ? "" : " " + trend);

                    TextView valTv = new TextView(this);
                    valTv.setText(valStr);
                    if (textSize > 0) valTv.setTextSize(textSize);
                    if (range != 0) {
                        valTv.setTextColor(range > 0 ? 0xFFFF0000 : 0xFF0000FF);
                        valTv.setTypeface(null, 1);
                    }
                    valTv.setOnClickListener(graphClick);

                    row.addView(nameTv);
                    row.addView(valTv);
                    sensorContainer.addView(row);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "parse: " + e.getMessage());
        }
    }
}
