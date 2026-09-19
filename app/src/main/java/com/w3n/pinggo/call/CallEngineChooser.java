package com.w3n.pinggo.call;
import android.app.Activity;
import android.view.ViewGroup;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import com.w3n.pinggo.views.common.NativePromptDialogView;
import java.util.ArrayList;
import java.util.List;
/** Native AAR per-call connection picker. */
public final class CallEngineChooser {
  public interface Listener { void onSelected(String engine); }
  private CallEngineChooser(){}
  public static void show(Activity activity,String mediaType,String chatId,Listener listener){
    boolean group=chatId!=null && chatId.startsWith("grp_");
    List<String> choices=new ArrayList<>();
    choices.add("LiveKit (1-to-1 and group)");
    if(!group)choices.add("WebRTC / JPEG (1-to-1 only)");
    choices.add("Cancel");
    if(activity.isFinishing() || activity.isDestroyed())return;
    ViewGroup root=activity.findViewById(android.R.id.content);
    NativePromptDialogView[] current={null};
    OnBackPressedCallback[] back={null};
    LifecycleEventObserver[] observer={null};
    Runnable dismiss=() -> {
      NativePromptDialogView view=current[0];current[0]=null;
      if(back[0]!=null)back[0].remove();
      if(activity instanceof ComponentActivity && observer[0]!=null)
        ((ComponentActivity)activity).getLifecycle().removeObserver(observer[0]);
      if(view!=null){root.removeView(view);view.release();}
    };
    current[0]=NativePromptDialogView.actions(activity,choices,index -> {
      if(index==choices.size()-1){activity.finish();return;}
      listener.onSelected(index==0?CallEngineToggle.LIVEKIT:CallEngineToggle.LEGACY);
    },dismiss);
    root.addView(current[0],new ViewGroup.LayoutParams(-1,-1));
    if(activity instanceof ComponentActivity){
      ComponentActivity owner=(ComponentActivity)activity;
      back[0]=new OnBackPressedCallback(true){@Override public void handleOnBackPressed(){dismiss.run();activity.finish();}};
      owner.getOnBackPressedDispatcher().addCallback(owner,back[0]);
      observer[0]=(source,event)->{if(event==Lifecycle.Event.ON_DESTROY)dismiss.run();};
      owner.getLifecycle().addObserver(observer[0]);
    }
  }
}
