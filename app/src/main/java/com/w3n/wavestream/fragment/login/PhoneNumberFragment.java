package com.w3n.wavestream.fragment.login;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.w3n.wavestream.R;
import com.w3n.wavestream.views.login.PhoneNumberLoginView;

/** The first screen in the login flow. */
public class PhoneNumberFragment extends Fragment {
    private PhoneNumberLoginView loginView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        loginView = new PhoneNumberLoginView(requireContext());
        loginView.setOnNextListener(this::openEmailFragment);
        return loginView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets navigationBarInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars());
            Insets keyboardInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            boolean isKeyboardVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime());
            if (loginView != null) {
                loginView.setInsets(
                        systemBarInsets.top,
                        Math.max(systemBarInsets.bottom, navigationBarInsets.bottom),
                        keyboardInsets.bottom,
                        isKeyboardVisible
                );
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    public boolean handleOutsideTap(float rawX, float rawY) {
        return loginView != null && loginView.handleCountryOutsideTap(rawX, rawY);
    }

    private void openEmailFragment(String fullPhoneNumber) {
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.login_fragment_container,
                        EmailFragment.newInstance(fullPhoneNumber))
                .addToBackStack(EmailFragment.class.getSimpleName())
                .commit();
    }

    @Override
    public void onDestroyView() {
        if (loginView != null) {
            loginView.setOnNextListener(null);
        }
        View view = getView();
        if (view != null) ViewCompat.setOnApplyWindowInsetsListener(view, null);
        loginView = null;
        super.onDestroyView();
    }
}
