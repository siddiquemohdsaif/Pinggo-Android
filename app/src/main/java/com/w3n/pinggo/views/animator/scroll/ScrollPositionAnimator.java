package com.w3n.pinggo.views.animator.scroll;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.widget.ScrollView;

public class ScrollPositionAnimator {
    private static final long DEFAULT_DURATION = 260L;

    private final ScrollView scrollView;
    private ValueAnimator valueAnimator;

    public ScrollPositionAnimator(ScrollView scrollView) {
        this.scrollView = scrollView;
    }

    public void scrollAnimateToPosition(float positionInPercentage) {
        int maxScroll = Math.max(0, scrollView.getChildAt(0).getHeight() - scrollView.getHeight());
        scrollAnimateToPixel(maxScroll * (positionInPercentage / 100f), DEFAULT_DURATION);
    }

    public void scrollAnimateToPixel(float scrollPos) {
        scrollAnimateToPixel(scrollPos, DEFAULT_DURATION);
    }

    public void scrollAnimateToPixel(float scrollPos, long duration) {
        closeAnimator();
        int start = scrollView.getScrollY();
        int end = Math.max(0, Math.round(scrollPos));
        valueAnimator = ValueAnimator.ofInt(start, end);
        valueAnimator.setDuration(duration);
        valueAnimator.addUpdateListener(animation ->
                scrollView.scrollTo(0, (Integer) animation.getAnimatedValue()));
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                valueAnimator = null;
            }
        });
        valueAnimator.start();
    }

    public void closeAnimator() {
        if (valueAnimator != null) {
            valueAnimator.cancel();
            valueAnimator = null;
        }
    }
}
