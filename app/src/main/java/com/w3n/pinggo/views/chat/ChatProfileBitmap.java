package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.Bitmap;
import com.w3n.pinggo.data.cache.ProfileBitmapCache;

/** Creates the circular chat profile bitmap without exposing bitmap preparation to ChatView. */
final class ChatProfileBitmap {
  private ChatProfileBitmap() {}

  static Bitmap load(Context context, String photoPath, String name, int fallbackSize, int accent) {
    return ProfileBitmapCache.get().request(photoPath, name, fallbackSize, accent, null);
  }
}
