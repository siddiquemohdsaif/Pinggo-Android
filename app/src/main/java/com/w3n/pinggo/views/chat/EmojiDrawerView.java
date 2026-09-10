package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bottom drawer backed by the packaged assets/emoji collection. */
public final class EmojiDrawerView extends FrameLayout {
  public interface Listener {
    void onEmojiSelected(String emoji);
    void onDismiss();
  }

  private final Listener listener;
  private final RecyclerView grid;
  private final ExecutorService loader = Executors.newFixedThreadPool(2);
  private volatile boolean released;

  private static final class Entry {
    final String title;
    final String asset;
    Entry(String title, String asset) { this.title = title; this.asset = asset; }
    boolean isHeader() { return asset == null; }
  }

  public EmojiDrawerView(Context context, Listener listener) {
    super(context);
    this.listener = listener;
    setClickable(true);
    setBackgroundColor(Color.WHITE);

    FrameLayout panel = new FrameLayout(context);
    panel.setBackgroundColor(Color.WHITE);
    LayoutParams panelParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.BOTTOM);
    addView(panel, panelParams);

    TextView title = new TextView(context);
    title.setText("Emoji");
    title.setTextColor(0xFF17212B);
    title.setTextSize(17);
    title.setGravity(Gravity.CENTER_VERTICAL);
    title.setPadding(dp(16), 0, dp(16), 0);
    panel.addView(title, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));

    View divider = new View(context);
    divider.setBackgroundColor(0xFFE5EAF0);
    FrameLayout.LayoutParams dividerParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(1));
    dividerParams.topMargin = dp(48);
    panel.addView(divider, dividerParams);

    grid = new RecyclerView(context);
    GridLayoutManager layout = new GridLayoutManager(context, 8);
    grid.setLayoutManager(layout);
    grid.setItemAnimator(null);
    grid.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
    FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    gridParams.topMargin = dp(49);
    panel.addView(grid, gridParams);
    panel.setOnClickListener(v -> { });

    loader.execute(() -> {
      List<Entry> entries = loadCollections();
      post(() -> {
        if (released) return;
        EmojiAdapter adapter = new EmojiAdapter(entries);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
          @Override public int getSpanSize(int position) {
            return adapter.isHeader(position) ? 8 : 1;
          }
        });
        grid.setAdapter(adapter);
      });
    });
  }

  public void dismiss() {
    if (listener != null) listener.onDismiss();
  }

  public void release() {
    released = true;
    loader.shutdownNow();
    grid.setAdapter(null);
  }

  private void collectAssets(String directory, List<String> result) {
    try {
      String[] names = getContext().getAssets().list(directory);
      if (names == null) return;
      for (String name : names) {
        String path = directory + "/" + name;
        if (name.toLowerCase(Locale.US).endsWith(".png")) result.add(path);
        else collectAssets(path, result);
      }
    } catch (IOException ignored) { }
  }

  private List<Entry> loadCollections() {
    List<Entry> entries = new ArrayList<>();
    try {
      String[] collections = getContext().getAssets().list("emoji");
      if (collections == null) return entries;
      List<String> sortedCollections = new ArrayList<>();
      Collections.addAll(sortedCollections, collections);
      Collections.sort(sortedCollections);
      for (String collection : sortedCollections) {
        String collectionPath = "emoji/" + collection;
        List<String> assets = new ArrayList<>();
        if (collection.toLowerCase(Locale.US).endsWith(".png")) assets.add(collectionPath);
        else collectAssets(collectionPath, assets);
        if (assets.isEmpty()) continue;
        Collections.sort(assets);
        entries.add(new Entry(collectionTitle(collection), null));
        for (String asset : assets) entries.add(new Entry(null, asset));
      }
    } catch (IOException ignored) { }
    return entries;
  }

  private static String collectionTitle(String value) {
    String[] words = value.replace('_', ' ').replace('-', ' ').trim().split("\\s+");
    StringBuilder title = new StringBuilder();
    for (String word : words) {
      if (word.isEmpty()) continue;
      if (title.length() > 0) title.append(' ');
      title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
    }
    return title.toString();
  }

  private static String unicodeForAsset(String path) {
    int marker = path.lastIndexOf("__");
    int extension = path.lastIndexOf('.');
    if (marker < 0 || extension <= marker + 2) return "";
    StringBuilder value = new StringBuilder();
    try {
      for (String code : path.substring(marker + 2, extension).split("-")) {
        value.appendCodePoint(Integer.parseInt(code, 16));
      }
      return value.toString();
    } catch (IllegalArgumentException error) {
      return "";
    }
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private final class EmojiAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int HEADER = 0;
    private static final int EMOJI = 1;
    private final List<Entry> entries;
    EmojiAdapter(List<Entry> entries) { this.entries = entries; }
    boolean isHeader(int position) { return entries.get(position).isHeader(); }

    @Override public int getItemViewType(int position) {
      return isHeader(position) ? HEADER : EMOJI;
    }

    @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      if (viewType == HEADER) {
        TextView title = new TextView(parent.getContext());
        title.setTextColor(0xFF687382);
        title.setTextSize(14);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), dp(8), dp(16), 0);
        title.setLayoutParams(new RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, dp(42)));
        return new HeaderHolder(title);
      }
      ImageView image = new ImageView(parent.getContext());
      image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
      image.setPadding(dp(7), dp(7), dp(7), dp(7));
      image.setBackground(new ColorDrawable(Color.TRANSPARENT));
      image.setLayoutParams(new RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));
      return new EmojiHolder(image);
    }

    @Override public void onBindViewHolder(RecyclerView.ViewHolder genericHolder, int position) {
      Entry entry = entries.get(position);
      if (genericHolder instanceof HeaderHolder) {
        ((HeaderHolder) genericHolder).title.setText(entry.title);
        return;
      }
      EmojiHolder holder = (EmojiHolder) genericHolder;
      String asset = entry.asset;
      holder.image.setTag(asset);
      holder.image.setImageDrawable(null);
      holder.image.setOnClickListener(v -> {
        String emoji = unicodeForAsset(asset);
        if (!emoji.isEmpty() && listener != null) listener.onEmojiSelected(emoji);
      });
      loader.execute(() -> {
        Bitmap bitmap = null;
        try (InputStream stream = getContext().getAssets().open(asset)) {
          bitmap = BitmapFactory.decodeStream(stream);
        } catch (IOException ignored) { }
        Bitmap decoded = bitmap;
        holder.image.post(() -> {
          if (!released && asset.equals(holder.image.getTag())) holder.image.setImageBitmap(decoded);
          else if (decoded != null) decoded.recycle();
        });
      });
    }

    @Override public int getItemCount() { return entries.size(); }
  }

  private static final class HeaderHolder extends RecyclerView.ViewHolder {
    final TextView title;
    HeaderHolder(TextView title) { super(title); this.title = title; }
  }

  private static final class EmojiHolder extends RecyclerView.ViewHolder {
    final ImageView image;
    EmojiHolder(ImageView image) { super(image); this.image = image; }
  }
}
