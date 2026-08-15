package com.w3n.pinggo.views.animator.scroll;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.widget.HorizontalScrollView;

public class ScrollPositionAnimatorHorizontal {
    private static final long DEFAULT_DURATION = 260L;

    private final HorizontalScrollView scrollView;
    private ValueAnimator valueAnimator;

    public ScrollPositionAnimatorHorizontal(HorizontalScrollView scrollView) {
        this.scrollView = scrollView;
    }

    public void scrollAnimateToPosition(float positionInPercentage) {
        int maxScroll = Math.max(0, scrollView.getChildAt(0).getWidth() - scrollView.getWidth());
        scrollAnimateToPixel(maxScroll * (positionInPercentage / 100f), DEFAULT_DURATION);
    }

    public void scrollAnimateToPixel(float scrollPos) {
        scrollAnimateToPixel(scrollPos, DEFAULT_DURATION);
    }

    public void scrollAnimateToPixel(float scrollPos, long duration) {
        closeAnimator();
        int start = scrollView.getScrollX();
        int end = Math.max(0, Math.round(scrollPos));
        valueAnimator = ValueAnimator.ofInt(start, end);
        valueAnimator.setDuration(duration);
        valueAnimator.addUpdateListener(animation ->
                scrollView.scrollTo((Integer) animation.getAnimatedValue(), 0));
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
