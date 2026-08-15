package com.w3n.pinggo.views.animator.button;

public class Region {
    private static final float TOUCH_SLOP = 10f;

    private float xMin;
    private float xMax;
    private float yMin;
    private float yMax;
    private final String id;

    public Region(float xMin, float xMax, float yMin, float yMax, String id) {
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public boolean isRegionClicked(float x, float y) {
        return x >= xMin && x <= xMax && y >= yMin && y <= yMax;
    }

    public boolean regionClickedDown(float x, float y) {
        return isRegionClicked(x, y);
    }

    public boolean regionClickedMove(float x, float y) {
        return x >= xMin - TOUCH_SLOP
                && x <= xMax + TOUCH_SLOP
                && y >= yMin - TOUCH_SLOP
                && y <= yMax + TOUCH_SLOP;
    }

    public void updateRegion(float left, float right, float top, float bottom) {
        this.xMin = left;
        this.xMax = right;
        this.yMin = top;
        this.yMax = bottom;
    }
}
