package cz.pstehlik.wifiteplomer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.ExpandableListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class TreeAdapter extends BaseExpandableListAdapter {
    private Context context;
    private List<String> groupList;
    private Map<String, List<TreeItem>> childMap;
    private LayoutInflater inflater;
    private ExpandableListView expandableListView;

    public TreeAdapter(Context context, List<String> groupList, Map<String, List<TreeItem>> childMap, ExpandableListView expandableListView) {
        this.context = context;
        this.groupList = groupList;
        this.childMap = childMap;
        this.inflater = LayoutInflater.from(context);
        this.expandableListView = expandableListView;
    }

    @Override
    public int getGroupCount() {
        return groupList.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        String groupName = groupList.get(groupPosition);
        return childMap.get(groupName).size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return groupList.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        String groupName = groupList.get(groupPosition);
        return childMap.get(groupName).get(childPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        String groupName = (String) getGroup(groupPosition);

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.group_item, null);
        }

        TextView groupTitle = convertView.findViewById(R.id.groupTitle);
        CheckBox groupCheckbox = convertView.findViewById(R.id.groupCheckbox);

        groupTitle.setText(groupName);

        // Set checkbox state based on children
        updateGroupCheckboxState(groupPosition, groupCheckbox);

        // Handle group checkbox click
        groupCheckbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isChecked = ((CheckBox) v).isChecked();
                selectAllChildren(groupPosition, isChecked);
                notifyDataSetChanged();
            }
        });

        // Handle title click for expand/collapse
        groupTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (expandableListView.isGroupExpanded(groupPosition)) {
                    expandableListView.collapseGroup(groupPosition);
                } else {
                    expandableListView.expandGroup(groupPosition);
                }
            }
        });

        // Make the entire group row clickable except for the checkbox
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (expandableListView.isGroupExpanded(groupPosition)) {
                    expandableListView.collapseGroup(groupPosition);
                } else {
                    expandableListView.expandGroup(groupPosition);
                }
            }
        });

        return convertView;
    }

    private void updateGroupCheckboxState(int groupPosition, CheckBox groupCheckbox) {
        String groupName = groupList.get(groupPosition);
        List<TreeItem> children = childMap.get(groupName);

        if (children == null || children.isEmpty()) {
            groupCheckbox.setChecked(false);
            return;
        }

        int selectedCount = 0;
        for (TreeItem child : children) {
            if (child.isSelected()) {
                selectedCount++;
            }
        }

        if (selectedCount == 0) {
            groupCheckbox.setChecked(false);
        } else if (selectedCount == children.size()) {
            groupCheckbox.setChecked(true);
        } else {
            // Partial selection - you can set indeterminate state if needed
            groupCheckbox.setChecked(false);
        }
    }

    private void selectAllChildren(int groupPosition, boolean isSelected) {
        String groupName = groupList.get(groupPosition);
        List<TreeItem> children = childMap.get(groupName);

        if (children != null) {
            for (TreeItem child : children) {
                child.setSelected(isSelected);
            }
        }
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        TreeItem item = (TreeItem) getChild(groupPosition, childPosition);

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.child_item, null);
        }

        TextView childName = convertView.findViewById(R.id.childName);
        CheckBox childCheckbox = convertView.findViewById(R.id.childCheckbox);

        String name = item.getName();
        if (!item.getUnit().isEmpty()) name += " [" + item.getUnit() + "]";
        childName.setText(name);

        // CRITICAL FIX: Clear the listener before setting the checked state
        // This prevents the listener from being triggered during view recycling
        childCheckbox.setOnCheckedChangeListener(null);

        // Set the checkbox state from the data model
        childCheckbox.setChecked(item.isSelected());

        // Now set the listener after the state is properly set
        childCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.setSelected(isChecked);
            // Update the parent group checkbox state
            notifyDataSetChanged();
        });

        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    public List<TreeItem> getSelectedItems() {
        List<TreeItem> selectedItems = new ArrayList<>();

        for (String groupName : groupList) {
            List<TreeItem> children = childMap.get(groupName);
            if (children != null) {
                for (TreeItem item : children) {
                    if (item.isSelected()) {
                        selectedItems.add(item);
                    }
                }
            }
        }

        return selectedItems;
    }
}
