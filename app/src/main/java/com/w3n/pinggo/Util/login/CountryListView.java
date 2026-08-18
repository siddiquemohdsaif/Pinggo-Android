package com.w3n.pinggo.Util.login;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.hbb20.CCPCountry;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

/** Native component-list host for country rows and their scroll gesture handling. */
public final class CountryListView extends View {
    private static final float ROW_HEIGHT_DP = 58f;

    private final ZLayerGroup nativeListLayers = new ZLayerGroup(this);
    private final ZLayer nativeListLayer = nativeListLayers.addLayer("countries");
    private final CountryAdapter adapter;
    private final CountryClickListener countryClickListener;
    private final KeyboardDelegate keyboardDelegate;
    private final int touchSlop;
    private float gestureDownY;
    private boolean keyboardDismissedForGesture;

    public CountryListView(Context context, CountryAdapter adapter,
                           CountryClickListener countryClickListener,
                           KeyboardDelegate keyboardDelegate) {
        super(context);
        this.adapter = adapter;
        this.countryClickListener = countryClickListener;
        this.keyboardDelegate = keyboardDelegate;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width <= 0 || height <= 0) return;
        nativeListLayer.clear();
        nativeListLayer.add(new ComponentList.Builder<CCPCountry>(getContext(),
                "country_native_list", new RectF(0f, 0f, width, height))
                .setOrientation(ComponentList.Orientation.VERTICAL)
                .setItemSize(dp(ROW_HEIGHT_DP))
                .setItemSpacing(0f)
                .setAdapter(adapter)
                .setOverscrollEnabled(false)
                .setClipToBounds(true)
                .setOnItemClickListener((list, country, position) ->
                        countryClickListener.onCountryClick(country)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        nativeListLayers.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gestureDownY = event.getY();
                keyboardDismissedForGesture = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!keyboardDismissedForGesture && keyboardDelegate.isKeyboardVisible()
                        && Math.abs(event.getY() - gestureDownY) > touchSlop) {
                    keyboardDismissedForGesture = true;
                    keyboardDelegate.hideKeyboard();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                keyboardDismissedForGesture = false;
                break;
            default:
                break;
        }
        return nativeListLayers.onTouchEvent(event) || super.onTouchEvent(event);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    public interface CountryClickListener {
        void onCountryClick(CCPCountry country);
    }

    public interface KeyboardDelegate {
        boolean isKeyboardVisible();

        void hideKeyboard();
    }
}
