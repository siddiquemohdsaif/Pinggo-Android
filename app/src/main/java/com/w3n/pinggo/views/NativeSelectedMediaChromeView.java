package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.component.FigmaConfig;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

/** Native-AAR chrome for the selected-media screen. */
public final class NativeSelectedMediaChromeView extends View {
  public static final int HEADER_COLOR = 0x66000000;
  public static final int EDITOR_NORMAL = 0;
  public static final int EDITOR_TEXT = 1;
  public static final int EDITOR_DRAW = 2;

  private final FigmaConfig figma=new FigmaConfig(1080f);
  private final ZLayerGroup layers=new ZLayerGroup(this);
  private final ZLayer chrome=layers.addLayer("selected_media_chrome");
  private final Bitmap header=color(HEADER_COLOR), footer=color(0x88000000);
  private final Bitmap secondary=color(0xFF3B4654), accent=color(0xFF019CC4);
  private final Listener listener; private final boolean captured; private final String senderId;
  private int topInset; private String title="Photo preview"; private boolean multiple;
  private boolean editorHeaderMode;
  private boolean fileMode;
  private boolean undoAvailable;
  private int editorMode = EDITOR_NORMAL;

  public NativeSelectedMediaChromeView(Context context,boolean captured,Listener listener){this(context,captured,"",listener);}
  public NativeSelectedMediaChromeView(Context context,boolean captured,String senderId,Listener listener){super(context);this.captured=captured;this.senderId=senderId==null?"":senderId;this.listener=listener;}
  public void setTopInset(int value){topInset=Math.max(0,value);build();}
  public void setState(String value,boolean multiple){title=value;this.multiple=multiple;build();}
  public void setMediaType(String type){fileMode="File".equals(type);editorHeaderMode=!fileMode;if(fileMode)editorMode=EDITOR_NORMAL;build();}
  public void setEditorMode(int value){editorMode=value;build();}
  public void setUndoAvailable(boolean value){undoAvailable=value;build();}
  @Override protected void onSizeChanged(int w,int h,int ow,int oh){build();}
  private void build(){float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;chrome.clear();float topH=topInset+px(198),bottomH=px(258.5f);
    if(editorHeaderMode){buildImageEditor(w,h);invalidate();return;}
    buildFilePreview(w,h);
    invalidate();}
  private void buildFilePreview(float w,float h){
    float size=px(112),top=topInset+px(34);
    chrome.add(button("file_back","×",secondary,new RectF(px(34),top,px(34)+size,top+size),id->listener.onBack(),30,Color.WHITE,size/2));
    chrome.add(text("file_name",title,new RectF(px(170),top,w-px(34),top+size),20,Text.Alignment.START));
    float footerTop=h-px(170);
    chrome.add(new Image.Builder(getContext(),"file_footer",footer,new RectF(0,footerTop,w,h)).setScaleType(Image.ScaleType.FIT_XY));
    chrome.add(button("file_sender",senderId,secondary,new RectF(px(22),footerTop+px(34),px(260),footerTop+px(136)),id->{},17,Color.WHITE,px(51)));
    chrome.add(button("file_send","➤",accent,new RectF(w-px(145),footerTop+px(20),w-px(25),footerTop+px(140)),id->listener.onSend(),32,Color.WHITE,px(60)));
  }
  private void buildImageEditor(float w,float h){
    if(editorMode==EDITOR_TEXT){
      chrome.add(button("editor_done","Done",secondary,new RectF(px(35),topInset+px(28),px(235),topInset+px(132)),id->listener.onDone(),19,Color.WHITE,px(52)));
      return;
    }
    if(editorMode==EDITOR_DRAW){
      chrome.add(button("draw_done","Done",secondary,new RectF(px(35),topInset+px(28),px(235),topInset+px(132)),id->listener.onDone(),19,Color.WHITE,px(52)));
      if(undoAvailable)chrome.add(button("draw_undo","↶",secondary,new RectF(w-px(275),topInset+px(28),w-px(165),topInset+px(138)),id->listener.onUndo(),31,Color.WHITE,px(55)));
      chrome.add(button("draw_active","✎",accent,new RectF(w-px(145),topInset+px(18),w-px(25),topInset+px(138)),id->listener.onDraw(),30,Color.WHITE,px(60)));
      return;
    }
    float size=px(112),top=topInset+px(34);
    float[] centers={90,350,570,790,1010};
    String[] labels={"×","↓","↻","Aa","✎"};
    for(int i=0;i<labels.length;i++){
      float center=px(centers[i]);
      final int action=i;
      chrome.add(button("editor_"+i,labels[i],secondary,new RectF(center-size/2,top,center+size/2,top+size),id->{
        if(action==0)listener.onBack();
        else if(action==1)listener.onDownload();
        else if(action==2)listener.onRotate();
        else if(action==3)listener.onText();
        else listener.onDraw();
      },i==3?22:30,Color.WHITE,size/2));
    }
    float footerTop=h-px(170);
    chrome.add(new Image.Builder(getContext(),"editor_footer",footer,new RectF(0,footerTop,w,h)).setScaleType(Image.ScaleType.FIT_XY));
    chrome.add(button("sender",senderId,secondary,new RectF(px(22),footerTop+px(34),px(260),footerTop+px(136)),id->{},17,Color.WHITE,px(51)));
    chrome.add(button("editor_send","➤",accent,new RectF(w-px(145),footerTop+px(20),w-px(25),footerTop+px(140)),id->listener.onSend(),32,Color.WHITE,px(60)));
  }
  private Button.Builder button(String id,String label,Bitmap bg,RectF rect,Button.OnClickListener click,float size){return new Button.Builder(getContext(),id,bg,label,rect).setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(px(49.5f)).setFont(NativeFonts.INTER).setFontVariations(FontVariation.SEMI_BOLD).setTextSizePx(sp(size)).setTextColor(Color.WHITE).setRippleEnabled(true).setRippleColor(0x33FFFFFF).setOnClickListener(click);}
  private Button.Builder button(String id,String label,Bitmap bg,RectF rect,Button.OnClickListener click,float size,int textColor,float radius){return new Button.Builder(getContext(),id,bg,label,rect).setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(radius).setFont(NativeFonts.INTER).setFontVariations(FontVariation.SEMI_BOLD).setTextSizePx(sp(size)).setTextColor(textColor).setRippleEnabled(true).setRippleColor(0x33FFFFFF).setOnClickListener(click);}
  private Text.Builder text(String id,String value,RectF rect,float size,Text.Alignment alignment){return new Text.Builder(getContext(),id,value,rect).setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR).setTextSizePx(sp(size)).setTextColor(Color.WHITE).setAlignment(alignment).setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(1);}
  @Override protected void onDraw(@NonNull Canvas c){super.onDraw(c);layers.draw(c);}@Override public boolean onTouchEvent(MotionEvent e){return layers.onTouchEvent(e);}
  public void release(){layers.release();recycle(header,footer,secondary,accent);}private float px(float v){return figma.toRuntime(v,Math.max(1,getResources().getDisplayMetrics().widthPixels));}private float sp(float v){return v*getResources().getDisplayMetrics().scaledDensity;}private static Bitmap color(int c){Bitmap b=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);b.eraseColor(c);return b;}private static void recycle(Bitmap...a){for(Bitmap b:a)if(b!=null&&!b.isRecycled())b.recycle();}
  public interface Listener{void onBack();void onRemove();void onRemoveAll();void onSend();default void onDownload(){}default void onRotate(){}default void onText(){}default void onDraw(){}default void onDone(){}default void onUndo(){}}
}
