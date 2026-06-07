package cz.pstehlik.wifiteplomer;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import androidx.appcompat.app.AppCompatActivity;

import static cz.pstehlik.wifiteplomer.AppWidgetViewsFactory.getWidgetPrefsName;

public class SelectSensorActivity extends AppCompatActivity {
    private static final String TAG = "SelectSensorActivity";
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private ExpandableListView expandableListView;
    private TreeAdapter treeAdapter;
    private List<String> groupList;
    private Map<String, List<TreeItem>> childMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_sensor);

        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        Log.d(TAG, "id = " + appWidgetId);

        initViews();
        initData();
        loadJsonData();
    }

    private void initViews() {
        expandableListView = findViewById(R.id.expandableListView);
        Button saveButton = findViewById(R.id.saveButton);

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSelectedItems(getTeplotyPreferences());
                finish();
            }
        });
    }

    private void initData() {
        groupList = new ArrayList<>();
        childMap = new HashMap<>();
    }

    private void loadJsonData() {
        AppWidgetViewsFactory.getTempData(getApplicationContext(), appWidgetId, new AppWidgetViewsFactory.TempDataCallback() {
                    @Override
                    public void onResult(String data) {
                        runOnUiThread(() -> {
                            parseJsonData(data);
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Error downloading data: " + e.getMessage());
                    }
                });
    }

    private void parseJsonData(final String data) {
        try {
            JSONObject dataObject = new JSONObject(data);
            JSONObject cidla = dataObject.getJSONObject("cidla");
            Iterator<String> keys = cidla.keys();

            groupList.clear();
            childMap.clear();

            List<String> selectedSensors = loadSelectedSensors(getTeplotyPreferences());

            while (keys.hasNext()) {
                String groupName = keys.next();
                groupList.add(groupName);

                JSONArray groupArray = cidla.getJSONArray(groupName);
                List<TreeItem> childList = new ArrayList<>();

                for (int i = 0; i < groupArray.length(); i++) {
                    JSONObject item = groupArray.getJSONObject(i);
                    String id = item.getString("i");
                    String name = item.getString("n");
                    int value = item.getInt("v");
                    String unit = item.getString("u");
                    int r = item.getInt("r");

                    TreeItem treeItem = new TreeItem(id, name, value, unit, r);
                    if (selectedSensors.contains(id)) treeItem.setSelected(true);

                    childList.add(treeItem);
                    Log.d(TAG, "Added " + treeItem.getName());
                }

                childMap.put(groupName, childList);
            }

            setupExpandableListView();

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON: " + e.getMessage());
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
        }
    }

    private void setupExpandableListView() {
        treeAdapter = new TreeAdapter(this, groupList, childMap, expandableListView);
        expandableListView.setAdapter(treeAdapter);

        // Expand all groups by default - use post to ensure adapter is ready
        expandableListView.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < groupList.size(); i++) {
                    expandableListView.expandGroup(i);
                }
            }
        });
    }

    public static List<String> loadSelectedSensors(final SharedPreferences teplotyPrefs) {
        String jsonString = teplotyPrefs.getString("selected_sensors", "");
        List<String> selectedIds = new ArrayList<>();
        try {
            if (jsonString != null && !jsonString.isEmpty()) {
                JSONArray jsonArray = new JSONArray(jsonString);
                for (int i = 0; i < jsonArray.length(); i++) {
                    selectedIds.add(jsonArray.getString(i));
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }
        Log.d(TAG, selectedIds.toString());
        return selectedIds;
    }

    private void saveSelectedItems(final SharedPreferences teplotyPrefs) {
        if (treeAdapter == null) return;
        List<TreeItem> selectedItems = treeAdapter.getSelectedItems();

        JSONArray jsonArray = new JSONArray();
        for (TreeItem item : selectedItems) {
            jsonArray.put(item.getId());
        }

        teplotyPrefs.edit().putString("selected_sensors", jsonArray.toString()).apply();

        Toast.makeText(this, R.string.saved, Toast.LENGTH_LONG).show();
        Log.d(TAG, selectedItems.toString());
    }

    private SharedPreferences getTeplotyPreferences() {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID)
            return getSharedPreferences(getWidgetPrefsName(appWidgetId), 0);
        return getSharedPreferences("TeplotyPrefs", 0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
