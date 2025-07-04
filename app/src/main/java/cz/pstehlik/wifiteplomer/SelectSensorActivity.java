package cz.pstehlik.wifiteplomer;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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

public class SelectSensorActivity extends AppCompatActivity {

    private ExpandableListView expandableListView;
    private TreeAdapter treeAdapter;
    private List<String> groupList;
    private Map<String, List<TreeItem>> childMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_sensor);

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
                collectSelectedItems();
            }
        });
    }

    private void initData() {
        groupList = new ArrayList<>();
        childMap = new HashMap<>();
    }

    private void loadJsonData() {
        Intent intent = getIntent();
        int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        String data = "";// AppWidgetViewsFactory.getTempData(getApplicationContext(), appWidgetId); // TODO FIXME must run asynchronously in a background threa
        parseJsonData(data);
    }

    private void parseJsonData(final String data) {
        try {
            JSONObject dataObject = new JSONObject(data);
            JSONObject cidla = dataObject.getJSONObject("cidla");
            Iterator<String> keys = cidla.keys();

            groupList.clear();
            childMap.clear();

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
                    childList.add(treeItem);
                    Log.d("SelectSensorActivity", "Added " + treeItem.getName());
                }

                childMap.put(groupName, childList);
            }

            setupExpandableListView();

        } catch (JSONException e) {
            Log.e("JSON_PARSE", "Error parsing JSON: " + e.getMessage());
            Toast.makeText(this, "Error parsing data", Toast.LENGTH_SHORT).show();
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

    private void collectSelectedItems() {
        if (treeAdapter == null) return;

        List<TreeItem> selectedItems = treeAdapter.getSelectedItems();

        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "No items selected", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder result = new StringBuilder();
        result.append("Selected items:\n");

        for (TreeItem item : selectedItems) {
            result.append("- ").append(item.getName())
                    .append(" (ID: ").append(item.getId()).append(")\n");
        }

        Toast.makeText(this, result.toString(), Toast.LENGTH_LONG).show();
        Log.d("SELECTED_ITEMS", result.toString());
    }
}
