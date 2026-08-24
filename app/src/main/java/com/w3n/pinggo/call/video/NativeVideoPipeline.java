package com.w3n.pinggo.call.video;

import android.content.res.AssetManager;
import android.view.Surface;

public final class NativeVideoPipeline {
    static { System.loadLibrary("camera_pipeline"); }
    private NativeVideoPipeline() {}

    public static native String nativeStart(boolean front, int width, int height);
    public static native void nativeStop();
    public static native String nativeSetSurface(Surface surface, AssetManager assets);
    public static native String nativeSetRemoteSurface(Surface surface, AssetManager assets);
    public static native void nativeSetMainPreviewRendering(boolean enabled);
    public static native void nativeSetDisplayRotation(int rotationDegrees);
    public static native boolean nativeRenderJpegToMainSurface(
            byte[] jpeg, int rotationDegrees, boolean mirror);
    public static native int[] nativeGetPreviewTransform();
}
