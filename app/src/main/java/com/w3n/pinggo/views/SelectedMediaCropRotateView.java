package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Full-screen crop/rotate surface used by selected image preview. */
final class SelectedMediaCropRotateView extends FrameLayout {
  interface Listener { void onCancel(); void onDone(Bitmap bitmap); }

  private final CropImageView cropView;

  SelectedMediaCropRotateView(Context context, Bitmap bitmap, Listener listener) {
    super(context);
    setBackgroundColor(Color.BLACK);
    setClickable(true);
    cropView = new CropImageView(context);
    cropView.setBackgroundColor(Color.BLACK);
    cropView.setFreeformCrop(true);
    cropView.setBitmap(bitmap);
    LayoutParams cropParams = new LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    cropParams.setMargins(0, dp(90), 0, dp(110));
    addView(cropView, cropParams);

    TextView cancel = action("Cancel");
    cancel.setOnClickListener(view -> listener.onCancel());
    LayoutParams cancelParams = new LayoutParams(dp(110), dp(72), Gravity.BOTTOM | Gravity.START);
    cancelParams.leftMargin = dp(20); cancelParams.bottomMargin = dp(18);
    addView(cancel, cancelParams);

    Button rotate = new Button(context);
    rotate.setText("↻"); rotate.setTextSize(34f); rotate.setTextColor(Color.WHITE);
    rotate.setBackgroundColor(Color.TRANSPARENT);
    rotate.setOnClickListener(view -> cropView.rotateClockwise());
    LayoutParams rotateParams = new LayoutParams(dp(90), dp(80), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    rotateParams.bottomMargin = dp(14);
    addView(rotate, rotateParams);

    TextView done = action("Done");
    done.setGravity(Gravity.CENTER);
    done.setOnClickListener(view -> {
      Bitmap result = cropView.getCroppedBitmap();
      if (result != null) listener.onDone(result);
    });
    LayoutParams doneParams = new LayoutParams(dp(110), dp(72), Gravity.BOTTOM | Gravity.END);
    doneParams.rightMargin = dp(20); doneParams.bottomMargin = dp(18);
    addView(done, doneParams);
  }

  private TextView action(String label) {
    TextView view = new TextView(getContext());
    view.setText(label); view.setTextColor(0xFF22C56E); view.setTextSize(18f);
    view.setGravity(Gravity.CENTER); view.setClickable(true);
    return view;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
