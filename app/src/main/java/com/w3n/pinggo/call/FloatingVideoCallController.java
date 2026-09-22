package com.w3n.pinggo.call;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.activity.HomeActivity;
import com.w3n.pinggo.activity.CallActivity;
import java.lang.ref.WeakReference;

/** In-app floating live preview for a minimized video call. */
public final class FloatingVideoCallController implements Application.ActivityLifecycleCallbacks {
  private static final com.ogfa.nativeviews.component.FigmaConfig FIGMA_CONFIG =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
  public interface SurfaceTarget { void attach(android.view.Surface surface); }
  private static final FloatingVideoCallController INSTANCE = new FloatingVideoCallController();
  private WeakReference<Activity> previousActivity = new WeakReference<>(null);
  private WeakReference<Activity> overlayHost = new WeakReference<>(null);
  private View overlay;
  private Text statusView;
  private ZLayerGroup overlayLayers;
  private Bitmap[] overlayBitmaps;
  private boolean active, minimized;
  private String status = "Connecting…";
  private Runnable endAction;
  private SurfaceTarget remoteSurfaceTarget;

  private FloatingVideoCallController() {}
  public static FloatingVideoCallController getInstance() { return INSTANCE; }
  public void initialize(Application app) { app.registerActivityLifecycleCallbacks(this); }
  public void begin(Runnable endAction, SurfaceTarget remoteSurfaceTarget) {
    this.endAction = endAction; this.remoteSurfaceTarget = remoteSurfaceTarget;
    status = "Connecting…"; active = true; minimized = false;
  }
  public boolean isMinimized() { return active && minimized; }
  public void updateStatus(String value) {
    status = value == null || value.trim().isEmpty() ? "Video call" : value;
    if (statusView != null) statusView.setText(status);
  }
  public void minimizeAndReturn(Activity callActivity) {
    minimized = true;
    Activity previous = previousActivity.get();
    Intent intent = new Intent(callActivity, previous != null && !previous.isFinishing()
        && !previous.isDestroyed() ? previous.getClass() : HomeActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    callActivity.startActivity(intent);
  }
  public void clear() {
    active = false; minimized = false; endAction = null; remoteSurfaceTarget = null; remove();
  }

  private void attach(Activity activity) {
    if (!active || !minimized || activity instanceof CallActivity) return;
    remove();
    ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
    FrameLayout card = new FrameLayout(activity); card.setElevation(px(activity, 33f));
    GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.BLACK);
    bg.setCornerRadius(px(activity, 38.5f)); bg.setStroke(px(activity, 5.5f), Color.WHITE);
    card.setBackground(bg); card.setClipToOutline(true);

    SurfaceView preview = new SurfaceView(activity);
    card.addView(preview, new FrameLayout.LayoutParams(-1, -1));
    preview.getHolder().addCallback(new SurfaceHolder.Callback() {
      @Override public void surfaceCreated(SurfaceHolder holder) {
        if (remoteSurfaceTarget != null) remoteSurfaceTarget.attach(holder.getSurface());
      }
      @Override public void surfaceChanged(SurfaceHolder holder, int f, int w, int h) {
        if (remoteSurfaceTarget != null) remoteSurfaceTarget.attach(holder.getSurface());
      }
      @Override public void surfaceDestroyed(SurfaceHolder holder) {
        if (minimized && remoteSurfaceTarget != null) remoteSurfaceTarget.attach(null);
      }
    });

    Bitmap shade=color(0x88000000), danger=color(0xFFE53935);
    overlayBitmaps=new Bitmap[]{shade,danger};
    final ZLayerGroup[] layerRef={null};
    View nativeChrome=new View(activity){
      final ZLayerGroup layers=new ZLayerGroup(this);
      final ZLayer content=layers.addLayer("floating_video_controls");
      {layerRef[0]=layers;}
      @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        content.clear();
        content.add(new com.ogfa.nativeviews.image.Image.Builder(activity,"status_shade",shade,
            new RectF(0,h-px(activity,88f),w,h)).setScaleType(Image.ScaleType.FIT_XY));
        statusView=content.add(new Text.Builder(activity,"status",status,
            new RectF(0,h-px(activity,88f),w,h))
            .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
            .setTextSizePx(px(activity,33f)).setTextColor(Color.WHITE)
            .setAlignment(Text.Alignment.CENTER).setVerticalAlignment(Text.VerticalAlignment.CENTER)
            .setMaxLines(1).setShadowPx(0,px(activity,1f),px(activity,4f),Color.BLACK));
        content.add(new Button.Builder(activity,"end",danger,"End",
            new RectF(w-px(activity,159.5f),px(activity,16.5f),w-px(activity,16.5f),px(activity,104.5f)))
            .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(px(activity,44f))
            .setFont(NativeFonts.INTER).setFontVariations(FontVariation.BOLD)
            .setTextSizePx(px(activity,33f)).setTextColor(Color.WHITE)
            .setRippleEnabled(true).setOnClickListener(id->{if(endAction!=null)endAction.run();}));
      }
      @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);layers.draw(canvas);}
      @Override public boolean onTouchEvent(MotionEvent event){return layers.onTouchEvent(event)||super.onTouchEvent(event);}
    };
    nativeChrome.setClickable(true);
    card.addView(nativeChrome,new FrameLayout.LayoutParams(-1,-1));
    overlayLayers=layerRef[0];
    makeDraggable(card, () -> restore(activity));

    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(px(activity, 330f),
        px(activity, 495f), Gravity.TOP | Gravity.END);
    params.setMargins(px(activity, 33f), px(activity, 198f), px(activity, 44f), 0);
    decor.addView(card, params); overlay = card; overlayHost = new WeakReference<>(activity);
  }
  private void restore(Activity host) {
    minimized = false; remove();
    ActiveCallRegistry.getInstance().openExisting(host);
  }
  private void makeDraggable(View view, Runnable tapAction) {
    final float[] down = new float[4];
    final boolean[] moved = new boolean[1];
    view.setOnTouchListener((v, event) -> {
      if (event.getAction() == MotionEvent.ACTION_DOWN) {
        down[0] = event.getRawX(); down[1] = event.getRawY(); down[2] = v.getX(); down[3] = v.getY();
        moved[0] = false;
        return true;
      }
      if (event.getAction() == MotionEvent.ACTION_MOVE) {
        float dx = event.getRawX() - down[0], dy = event.getRawY() - down[1];
        if (Math.hypot(dx, dy) > px((Activity) v.getContext(), 22f)) moved[0] = true;
        View parent = (View) v.getParent();
        float maxX = Math.max(0, parent.getWidth() - v.getWidth());
        float maxY = Math.max(0, parent.getHeight() - v.getHeight());
        v.setX(Math.max(0, Math.min(maxX, down[2] + dx)));
        v.setY(Math.max(0, Math.min(maxY, down[3] + dy)));
        return true;
      }
      if (event.getAction() == MotionEvent.ACTION_UP) {
        if (!moved[0] && tapAction != null) tapAction.run();
        return true;
      }
      if (event.getAction() == MotionEvent.ACTION_CANCEL) {
        return true;
      }
      return true;
    });
  }
  private void remove() {
    if (overlay != null && overlay.getParent() instanceof ViewGroup)
      ((ViewGroup) overlay.getParent()).removeView(overlay);
    overlay = null; statusView = null; overlayHost.clear();
    if(overlayLayers!=null){overlayLayers.release();overlayLayers=null;}
    if(overlayBitmaps!=null){for(Bitmap bitmap:overlayBitmaps)if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();overlayBitmaps=null;}
  }
  private static Bitmap color(int value){Bitmap bitmap=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);bitmap.eraseColor(value);return bitmap;}
  private static int px(Activity activity, float value) {
    return Math.round(FIGMA_CONFIG.toRuntime(value,
        Math.max(1, activity.getResources().getDisplayMetrics().widthPixels)));
  }
  @Override public void onActivityResumed(Activity activity) {
    if (activity instanceof CallActivity) { minimized = false; remove(); }
    else { previousActivity = new WeakReference<>(activity); attach(activity); }
  }
  @Override public void onActivityDestroyed(Activity activity) {}
  @Override public void onActivityCreated(Activity activity, Bundle state) {}
  @Override public void onActivityStarted(Activity activity) {}
  @Override public void onActivityPaused(Activity activity) {}
  @Override public void onActivityStopped(Activity activity) {}
  @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
}
