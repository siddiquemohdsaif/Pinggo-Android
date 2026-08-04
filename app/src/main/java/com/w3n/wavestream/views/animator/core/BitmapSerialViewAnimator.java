package com.w3n.wavestream.views.animator.core;

import android.graphics.Canvas;

import java.util.ArrayList;
import java.util.Iterator;

public class BitmapSerialViewAnimator {
    private final ArrayList<AnimationProperties> queue = new ArrayList<>();
    private final BitmapViewAnimator currentAnimator = new BitmapViewAnimator();
    private final String animName;
    private final boolean repeat;
    private int currentIndex;
    private boolean stopped;

    public BitmapSerialViewAnimator(String animName, boolean repeat) {
        this.animName = animName;
        this.repeat = repeat;
    }

    public String getAnimName() {
        return animName;
    }

    public void addSerialAnimations(ArrayList<AnimationProperties> animations) {
        queue.clear();
        queue.addAll(animations);
        currentIndex = 0;
        stopped = false;
        startNextAnimation();
    }

    public void stopAnimation() {
        stopped = true;
        currentAnimator.stopAnimation();
    }

    public boolean isFinished() {
        return stopped || (!repeat && currentIndex >= queue.size() && currentAnimator.isFinished());
    }

    public void onDraw(Canvas canvas) {
        if (queue.isEmpty() || stopped) {
            return;
        }

        currentAnimator.onDraw(canvas);
        if (currentAnimator.isFinished()) {
            currentIndex++;
            if (currentIndex >= queue.size()) {
                if (!repeat) {
                    return;
                }
                currentIndex = 0;
            }
            startNextAnimation();
        }
    }

    private void startNextAnimation() {
        if (queue.isEmpty() || currentIndex >= queue.size()) {
            return;
        }

        AnimationProperties properties = queue.get(currentIndex);
        currentAnimator.startAnimation(animName, properties.bitmap, properties.startAlpha,
                properties.endAlpha, properties.startWidth, properties.endWidth,
                properties.startHeight, properties.endHeight, properties.startPosX,
                properties.endPosX, properties.startPosY, properties.endPosY,
                properties.duration, false, null, false);
    }

    public static void addSerialAnimations(ArrayList<BitmapSerialViewAnimator> animators,
                                           ArrayList<AnimationProperties> animations,
                                           String animName, boolean repeat) {
        removeAnimation(animName, animators);
        BitmapSerialViewAnimator animator = new BitmapSerialViewAnimator(animName, repeat);
        animator.addSerialAnimations(animations);
        animators.add(animator);
    }

    public static void removeAnimation(String animName, ArrayList<BitmapSerialViewAnimator> animators) {
        for (Iterator<BitmapSerialViewAnimator> iterator = animators.iterator(); iterator.hasNext(); ) {
            if (animName.equals(iterator.next().getAnimName())) {
                iterator.remove();
            }
        }
    }

    public static void Draw(Canvas canvas, ArrayList<BitmapSerialViewAnimator> animators) {
        for (Iterator<BitmapSerialViewAnimator> iterator = animators.iterator(); iterator.hasNext(); ) {
            BitmapSerialViewAnimator animator = iterator.next();
            animator.onDraw(canvas);
            if (animator.isFinished()) {
                iterator.remove();
            }
        }
    }
}
