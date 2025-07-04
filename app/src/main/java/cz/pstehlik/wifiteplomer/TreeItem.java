package cz.pstehlik.wifiteplomer;

class TreeItem {
    private String id;
    private String name;
    private int value;
    private String unit;
    private int r;
    private boolean isSelected;

    public TreeItem(String id, String name, int value, String unit, int r) {
        this.id = id;
        this.name = name;
        this.value = value;
        this.unit = unit;
        this.r = r;
        this.isSelected = false;
    }

    // Getters and setters
    public String getId() { return id; }
    public String getName() { return name; }
    public int getValue() { return value; }
    public String getUnit() { return unit; }
    public int getR() { return r; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
}
