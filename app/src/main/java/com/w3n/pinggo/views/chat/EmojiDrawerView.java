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
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.zlayer.*;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.button.Button;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;

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
  private final EmojiGrid grid;
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

    grid = new EmojiGrid(context);
    FrameLayout.LayoutParams gridParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    panel.addView(grid, gridParams);
    panel.setOnClickListener(v -> { });

    loader.execute(() -> {
      List<Entry> entries = loadCollections();
      post(() -> {
        if (released) return;
        grid.submit(entries);
      });
    });
  }

  public void dismiss() {
    if (listener != null) listener.onDismiss();
  }

  public void release() {
    released = true;
    loader.shutdownNow();
    grid.release();
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

  private final class EmojiGrid extends View {
    final ZLayerGroup layers=new ZLayerGroup(this);
    final ZLayer layer=layers.addLayer("emoji_grid");
    final List<List<Entry>> rows=new ArrayList<>();
    final android.util.LruCache<String,Bitmap> bitmaps=new android.util.LruCache<String,Bitmap>(8*1024*1024){
      @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}
    };
    final java.util.Set<String> pending=new java.util.HashSet<>();
    final Bitmap empty=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);
    final RowAdapter adapter=new RowAdapter();
    EmojiGrid(Context context){super(context);setClickable(true);}
    void submit(List<Entry> entries){
      rows.clear();rows.add(java.util.Collections.singletonList(new Entry("Emoji",null)));List<Entry> current=null;
      for(Entry entry:entries){
        if(entry.isHeader()){rows.add(java.util.Collections.singletonList(entry));current=null;}
        else {if(current==null||current.size()==8){current=new ArrayList<>();rows.add(current);}current.add(entry);}
      }adapter.notifyDataSetChanged();
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
      layer.clear();layer.add(new ComponentList.Builder<List<Entry>>(getContext(),"emoji_rows",new RectF(0,0,w,h))
          .setOrientation(ComponentList.Orientation.VERTICAL).setItemSizeProvider((row,position)->row.get(0).isHeader()?dp(36):Math.max(dp(44),w/8f))
          .setAdapter(adapter).setClipToBounds(true));
    }
    void release(){
      layers.release();
      java.util.Map<String,Bitmap> cached=bitmaps.snapshot();
      // LruCache must calculate each entry's original size while evicting it.
      // Recycling first changes getAllocationByteCount() and violates that invariant.
      bitmaps.evictAll();
      for(Bitmap bitmap:cached.values())if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();
      if(!empty.isRecycled())empty.recycle();
    }
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);layers.draw(canvas);}
    @Override public boolean onTouchEvent(MotionEvent event){return layers.onTouchEvent(event)||super.onTouchEvent(event);}
    final class RowAdapter extends ComponentList.Adapter<List<Entry>> {
      @Override public int getItemCount(){return rows.size();}
      @Override public List<Entry> getItem(int position){return rows.get(position);}
      @Override public int getItemViewType(int position){return getItem(position).get(0).isHeader()?1:0;}
      @Override public void onCreateItem(ComponentList.Item item,int type){
        ZLayer row=item.addLayer("row");float w=item.getScope().width(),h=item.getScope().height();
        if(type==1){row.add(new Text.Builder(getContext(),item.getScope().id("header"),"",new RectF(dp(12),0,w,h)).setTextColor(0xFF657381).setTextSizePx(dp(13)).setVerticalAlignment(Text.VerticalAlignment.CENTER));return;}
        for(int i=0;i<8;i++){
          final int column=i;float left=w*i/8f;
          row.add(new Image.Builder(getContext(),item.getScope().id("emoji_"+i),empty,new RectF(left+dp(6),dp(6),left+w/8f-dp(6),h-dp(6))).setScaleType(Image.ScaleType.FIT_CENTER));
          row.add(new Button.Builder(getContext(),item.getScope().id("touch_"+i),empty,"",new RectF(left,0,left+w/8f,h))
              .setRippleEnabled(false).setOnClickListener(id->{int position=item.getPosition();if(position>=0&&position<rows.size()&&column<rows.get(position).size()){
                String emoji=unicodeForAsset(rows.get(position).get(column).asset);if(!emoji.isEmpty()&&listener!=null)listener.onEmojiSelected(emoji);
              }}));
        }
      }
      @Override public void onBindItem(ComponentList.Item holder,List<Entry> row,int position){
        if(row.get(0).isHeader()){holder.find("header",Text.class).setText(row.get(0).title);return;}
        for(int i=0;i<8;i++){
          Image image=holder.find("emoji_"+i,Image.class);Button touch=holder.find("touch_"+i,Button.class);
          image.setVisible(i<row.size());touch.setVisible(i<row.size());if(i>=row.size())continue;
          String asset=row.get(i).asset;Bitmap bitmap=bitmaps.get(asset);image.setBitmap(bitmap==null?empty:bitmap);
          if(bitmap==null&&pending.add(asset))loader.execute(()->{
            Bitmap decoded=null;try(InputStream stream=getContext().getAssets().open(asset)){decoded=BitmapFactory.decodeStream(stream);}catch(IOException ignored){}
            Bitmap result=decoded;post(()->{pending.remove(asset);if(released){if(result!=null)result.recycle();return;}if(result!=null){bitmaps.put(asset,result);adapter.notifyDataSetChanged();}});
          });
        }
      }
    }
  }
}
