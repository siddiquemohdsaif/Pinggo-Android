package com.w3n.pinggo.views.common;

import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

/** Installs task-root back handling backed by {@link ExitAppDialogView}. */
public final class ExitAppController {
    private ExitAppController() {
    }

    public static void install(AppCompatActivity activity,
                               @Nullable FragmentManager fragmentManager) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        ExitAppDialogView dialogView = new ExitAppDialogView(activity,
                activity::finishAndRemoveTask);
        content.addView(dialogView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        activity.getOnBackPressedDispatcher().addCallback(activity,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (dialogView.dismissIfShowing()) return;
                        if (fragmentManager != null
                                && fragmentManager.getBackStackEntryCount() > 0) {
                            fragmentManager.popBackStack();
                            return;
                        }
                        if (!activity.isTaskRoot()) {
                            activity.finish();
                            return;
                        }
                        dialogView.showDialog();
                    }
                });
    }
}
