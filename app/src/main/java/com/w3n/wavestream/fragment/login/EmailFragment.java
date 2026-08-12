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

import com.w3n.wavestream.views.login.EmailLoginView;

/** Collects the email after the phone-number step has been validated. */
public class EmailFragment extends Fragment {
    private static final String ARG_PHONE_NUMBER = "phone_number";
    private EmailLoginView loginView;

    public static EmailFragment newInstance(@NonNull String fullPhoneNumber) {
        EmailFragment fragment = new EmailFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_PHONE_NUMBER, fullPhoneNumber);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        String phoneNumber = requireArguments().getString(ARG_PHONE_NUMBER, "");
        loginView = new EmailLoginView(requireContext(), phoneNumber);
        loginView.setOnBackListener(() -> getParentFragmentManager().popBackStack());
        loginView.setOnNextListener(this::openOtpFragment);
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

    private void openOtpFragment(String fullPhoneNumber, String email) {
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(com.w3n.wavestream.R.id.login_fragment_container,
                        OtpFragment.newInstance(fullPhoneNumber, email))
                .addToBackStack(OtpFragment.class.getSimpleName())
                .commit();
    }

    @Override
    public void onDestroyView() {
        View view = getView();
        if (view != null) ViewCompat.setOnApplyWindowInsetsListener(view, null);
        if (loginView != null) {
            loginView.setOnNextListener(null);
            loginView.setOnBackListener(null);
        }
        loginView = null;
        super.onDestroyView();
    }
}
