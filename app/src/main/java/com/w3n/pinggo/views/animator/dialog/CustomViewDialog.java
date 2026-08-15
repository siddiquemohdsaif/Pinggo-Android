package com.w3n.pinggo.views.animator.dialog;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import com.w3n.pinggo.views.animator.button.Region;

import java.util.ArrayList;
import java.util.Iterator;

public abstract class CustomViewDialog {
    private boolean closable;
    private String id;
    private CustomDialogCallback callback;
    private boolean closed;

    public void onCreate(View view, boolean closable, String id, CustomDialogCallback callback) {
        this.closable = closable;
        this.id = id;
        this.callback = callback;
        this.closed = false;
    }

    public abstract void onDraw(Canvas canvas);

    public String getId() {
        return id;
    }

    public abstract boolean onTouchEvent(MotionEvent event);

    public void closedDialog() {
        closed = true;
        if (callback != null) {
            callback.onClosed(id);
        }
    }

    public boolean isClosed() {
        return closed;
    }

    protected boolean closeOutsideClick(MotionEvent event, Region contentRegion) {
        if (!closable || event.getActionMasked() != MotionEvent.ACTION_UP) {
            return false;
        }
        if (!contentRegion.isRegionClicked(event.getX(), event.getY())) {
            closedDialog();
            return true;
        }
        return false;
    }

    public static void addDialog(ArrayList<CustomViewDialog> dialogs, CustomViewDialog dialog,
                                 View view, boolean closable, String id,
                                 CustomDialogCallback callback) {
        dialog.onCreate(view, closable, id, callback);
        dialogs.add(dialog);
        view.invalidate();
    }

    public static boolean removeDialog(String id, ArrayList<CustomViewDialog> dialogs) {
        for (Iterator<CustomViewDialog> iterator = dialogs.iterator(); iterator.hasNext(); ) {
            CustomViewDialog dialog = iterator.next();
            if (id.equals(dialog.getId())) {
                dialog.closedDialog();
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public static boolean HandleTouch(MotionEvent event, ArrayList<CustomViewDialog> dialogs) {
        for (int i = dialogs.size() - 1; i >= 0; i--) {
            CustomViewDialog dialog = dialogs.get(i);
            if (dialog.onTouchEvent(event)) {
                if (dialog.isClosed()) {
                    dialogs.remove(i);
                }
                return true;
            }
        }
        return false;
    }

    public static void Draw(Canvas canvas, ArrayList<CustomViewDialog> dialogs) {
        for (CustomViewDialog dialog : dialogs) {
            dialog.onDraw(canvas);
        }
    }

    public interface CustomDialogCallback {
        void onClosed(String id);
    }
}
