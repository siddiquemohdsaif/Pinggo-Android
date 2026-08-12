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

import com.w3n.wavestream.views.login.FlashCallLoginView;

/** Hosts the automatic flash-call verification step. */
public class FlashCallFragment extends Fragment {
    private static final String ARG_PHONE_NUMBER = "phone_number";
    private static final String ARG_EMAIL = "email";
    private FlashCallLoginView loginView;

    public static FlashCallFragment newInstance(@NonNull String phoneNumber,
                                                @NonNull String email) {
        FlashCallFragment fragment = new FlashCallFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_PHONE_NUMBER, phoneNumber);
        arguments.putString(ARG_EMAIL, email);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        String phoneNumber = requireArguments().getString(ARG_PHONE_NUMBER, "");
        loginView = new FlashCallLoginView(requireContext(), phoneNumber);
        loginView.setOnBackListener(() -> getParentFragmentManager().popBackStack());
        loginView.setOnSmsAvailableListener(this::openSmsOtp);
        return loginView;
    }

    private void openSmsOtp() {
        String phoneNumber = requireArguments().getString(ARG_PHONE_NUMBER, "");
        String email = requireArguments().getString(ARG_EMAIL, "");
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(com.w3n.wavestream.R.id.login_fragment_container,
                        OtpFragment.newSmsInstance(phoneNumber, email))
                .addToBackStack(OtpFragment.class.getSimpleName() + "_Sms")
                .commit();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets navigationBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars());
            if (loginView != null) {
                loginView.setInsets(systemBars.top,
                        Math.max(systemBars.bottom, navigationBars.bottom));
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    @Override
    public void onDestroyView() {
        View view = getView();
        if (view != null) ViewCompat.setOnApplyWindowInsetsListener(view, null);
        if (loginView != null) {
            loginView.setOnBackListener(null);
            loginView.setOnSmsAvailableListener(null);
        }
        loginView = null;
        super.onDestroyView();
    }
}
