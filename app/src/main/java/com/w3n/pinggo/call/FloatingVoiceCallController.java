package com.w3n.pinggo.call;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
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
import com.w3n.pinggo.activity.ChatActivity;
import com.w3n.pinggo.activity.CallActivity;
import java.lang.ref.WeakReference;

/** In-app floating card for a minimized active voice call. */
public final class FloatingVoiceCallController implements Application.ActivityLifecycleCallbacks {
  private static final com.ogfa.nativeviews.component.FigmaConfig FIGMA_CONFIG =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
  private static final FloatingVoiceCallController INSTANCE = new FloatingVoiceCallController();
  private Application application;
  private WeakReference<Activity> resumedActivity = new WeakReference<>(null);
  private WeakReference<Activity> previousActivity = new WeakReference<>(null);
  private View overlay;
  private WeakReference<Activity> overlayHost = new WeakReference<>(null);
  private Text statusView;
  private ZLayerGroup overlayLayers;
  private Bitmap[] overlayBitmaps;
  private boolean active, minimized;
  private String phone = "Unknown", profilePath, status = "Calling…";
  private Runnable endAction;

  private FloatingVoiceCallController() {}
  public static FloatingVoiceCallController getInstance() { return INSTANCE; }

  public void initialize(Application app) {
    if (application != null) return;
    application = app;
    app.registerActivityLifecycleCallbacks(this);
  }
  public void begin(String phone, String profilePath, Runnable endAction) {
    this.phone = phone == null || phone.trim().isEmpty() ? "Unknown" : phone;
    this.profilePath = profilePath;
    this.endAction = endAction;
    status = "Calling…";
    active = true;
  }
  public void minimizeAndReturn(Activity callActivity) {
    minimized = true;
    Activity previous = previousActivity.get();
    Intent intent;
    if (previous != null && !previous.isFinishing() && !previous.isDestroyed()) {
      intent = new Intent(callActivity, previous.getClass());
    } else {
      intent = new Intent(callActivity, HomeActivity.class);
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    callActivity.startActivity(intent);
  }
  public void updateStatus(String value) {
    status = value == null || value.trim().isEmpty() ? "Calling…" : value;
    if (statusView != null) statusView.setText(status);
  }
  public void clear() { active = false; minimized = false; endAction = null; remove(); }

  private void attach(Activity activity) {
    if (!active || !minimized || activity instanceof CallActivity) return;
    remove();
    ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
    Bitmap white=color(Color.WHITE), danger=color(0xFFE53935), fallback=color(0xFFD9F1F7);
    Bitmap avatar=profilePath == null || profilePath.trim().isEmpty()
        ? fallback : BitmapFactory.decodeFile(profilePath);
    if(avatar==null)avatar=fallback;
    final Bitmap avatarBitmap=avatar;
    overlayBitmaps=new Bitmap[]{white,danger,fallback,avatarBitmap};
    final ZLayerGroup[] layerRef={null};
    View card=new View(activity){
      final ZLayerGroup layers=new ZLayerGroup(this);
      final ZLayer content=layers.addLayer("floating_voice_call");
      {layerRef[0]=layers;}
      @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        content.clear();
        content.add(new Button.Builder(activity,"restore_call",white,"",new RectF(0,0,w,h))
            .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(px(activity,49.5f))
            .setRippleEnabled(true).setOnClickListener(id->{
              ActiveCallRegistry.getInstance().openExisting(activity);
            }));
        content.add(new Button.Builder(activity,"avatar",avatarBitmap,"",
            new RectF(px(activity,33f),px(activity,33f),px(activity,165f),px(activity,165f)))
            .setImageScaleType(Image.ScaleType.CENTER_CROP).setCornerRadiusPx(px(activity,66f)).setRippleEnabled(false));
        content.add(new Text.Builder(activity,"phone",phone,
            new RectF(px(activity,198f),px(activity,32f),w-px(activity,220f),px(activity,98f)))
            .setFont(NativeFonts.INTER).setFontVariations(FontVariation.BOLD)
            .setTextSizePx(px(activity,44f)).setTextColor(0xFF000E1A)
            .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(1));
        statusView=content.add(new Text.Builder(activity,"status",status,
            new RectF(px(activity,198f),px(activity,96f),w-px(activity,220f),px(activity,158f)))
            .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
            .setTextSizePx(px(activity,35.75f)).setTextColor(0xFF019CC4)
            .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(1));
        content.add(new Button.Builder(activity,"end",danger,"End",
            new RectF(w-px(activity,198f),px(activity,38.5f),w-px(activity,22f),px(activity,159.5f)))
            .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(px(activity,60.5f))
            .setFont(NativeFonts.INTER).setFontVariations(FontVariation.BOLD)
            .setTextSizePx(px(activity,38.5f)).setTextColor(Color.WHITE)
            .setRippleEnabled(true).setOnClickListener(id->{if(endAction!=null)endAction.run();}));
      }
      @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);layers.draw(canvas);}
      @Override public boolean onTouchEvent(MotionEvent event){return layers.onTouchEvent(event)||super.onTouchEvent(event);}
    };
    overlayLayers=layerRef[0];
    card.setClickable(true);card.setElevation(px(activity,27.5f));
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(activity, 198f), Gravity.TOP);
    params.setMargins(px(activity, 33f), px(activity, 121f), px(activity, 33f), 0);
    decor.addView(card, params);
    overlay = card;
    overlayHost = new WeakReference<>(activity);
    if (activity instanceof ChatActivity) {
      ((ChatActivity) activity).setFloatingCallInset(px(activity, 253f));
    }
  }
  private void remove() {
    Activity host = overlayHost.get();
    if (host instanceof ChatActivity) ((ChatActivity) host).setFloatingCallInset(0);
    if (overlay != null && overlay.getParent() instanceof ViewGroup) ((ViewGroup) overlay.getParent()).removeView(overlay);
    overlay = null; statusView = null; overlayHost.clear();
    if(overlayLayers!=null){overlayLayers.release();overlayLayers=null;}
    if(overlayBitmaps!=null){
      java.util.Set<Bitmap> unique=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
      java.util.Collections.addAll(unique,overlayBitmaps);
      for(Bitmap bitmap:unique)if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();
      overlayBitmaps=null;
    }
  }
  private static Bitmap color(int value){Bitmap bitmap=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);bitmap.eraseColor(value);return bitmap;}
  private static int px(Activity activity, float value) {
    return Math.round(FIGMA_CONFIG.toRuntime(value,
        Math.max(1, activity.getResources().getDisplayMetrics().widthPixels)));
  }

  @Override public void onActivityResumed(Activity activity) {
    resumedActivity = new WeakReference<>(activity);
    if (activity instanceof CallActivity) {
      remove();
    } else {
      previousActivity = new WeakReference<>(activity);
      attach(activity);
    }
  }
  @Override public void onActivityDestroyed(Activity activity) { if (resumedActivity.get() == activity) resumedActivity.clear(); }
  @Override public void onActivityCreated(Activity activity, Bundle state) {}
  @Override public void onActivityStarted(Activity activity) {}
  @Override public void onActivityPaused(Activity activity) {}
  @Override public void onActivityStopped(Activity activity) {}
  @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
}
