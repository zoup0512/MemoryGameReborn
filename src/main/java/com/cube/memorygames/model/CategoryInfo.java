package com.cube.memorygames.model;

public class CategoryInfo {
    private int backgroundColorResId;
    private int colorResId;
    private int gridColorResId;
    private String id;
    private int nameResId;

    public CategoryInfo(int nameResId, String id, int colorResId, int backgroundColorResId, int gridColorResId) {
        this.nameResId = nameResId;
        this.id = id;
        this.colorResId = colorResId;
        this.backgroundColorResId = backgroundColorResId;
        this.gridColorResId = gridColorResId;
    }

    public int getNameResId() {
        return this.nameResId;
    }

    public String getId() {
        return this.id;
    }

    public int getColorResId() {
        return this.colorResId;
    }

    public int getBackgroundColorResId() {
        return this.backgroundColorResId;
    }

    public int getGridColorResId() {
        return this.gridColorResId;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return this.id.equals(((CategoryInfo) o).id);
    }

    public int hashCode() {
        return this.id.hashCode();
    }
}
