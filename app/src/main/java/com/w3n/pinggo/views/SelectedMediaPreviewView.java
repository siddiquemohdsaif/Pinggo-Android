package com.w3n.pinggo.views;
import android.content.Context;
import android.net.Uri;
import java.util.List;
/** Public complete selected-media view; implementation lives in the reusable overlay base. */
public final class SelectedMediaPreviewView extends SelectedMediaOverlayView {
  public SelectedMediaPreviewView(Context context, List<Uri> uris, List<String> types,
      boolean captured, Listener listener) {
    super(context, uris, types, captured, listener);
  }

  public SelectedMediaPreviewView(Context context, List<Uri> uris, List<String> types,
      boolean captured, String senderId, Listener listener) {
    super(context, uris, types, captured, senderId, listener);
  }
}
