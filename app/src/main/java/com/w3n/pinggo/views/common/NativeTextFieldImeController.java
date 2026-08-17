package com.w3n.pinggo.views.common;

import android.content.Context;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.ogfa.nativeviews.textfield.TextField;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

/** Restores outside-tap IME dismissal when other native components cover the screen. */
public final class NativeTextFieldImeController {
    private NativeTextFieldImeController() {
    }

    public static void dismissOnOutsideDown(View host, ZLayerGroup layers,
                                            ZLayer textFieldLayer, MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) return;
        TextField focusedField = layers.getFocusedTextField();
        if (focusedField == null) return;

        RectF translatedBounds = new RectF(focusedField.getBounds());
        if (textFieldLayer != null) {
            translatedBounds.offset(textFieldLayer.getTranslationX(),
                    textFieldLayer.getTranslationY());
        }
        if (translatedBounds.contains(event.getX(), event.getY())) return;

        focusedField.clearFocus();
        InputMethodManager inputMethodManager = (InputMethodManager)
                host.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(host.getWindowToken(), 0);
        }
        host.invalidate();
    }
}
