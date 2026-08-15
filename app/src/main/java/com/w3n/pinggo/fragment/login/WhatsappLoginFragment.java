package com.w3n.pinggo.fragment.login;

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

import com.w3n.pinggo.views.login.WhatsappLoginView;

/** The WhatsApp login step reached after successful email OTP verification. */
public class WhatsappLoginFragment extends Fragment {
    private static final String ARG_PHONE_NUMBER = "phone_number";
    private static final String ARG_EMAIL = "email";
    private WhatsappLoginView loginView;

    public static WhatsappLoginFragment newInstance(@NonNull String fullPhoneNumber,
                                                     @NonNull String email) {
        WhatsappLoginFragment fragment = new WhatsappLoginFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_PHONE_NUMBER, fullPhoneNumber);
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
        loginView = new WhatsappLoginView(requireContext(), phoneNumber);
        loginView.setOnBackListener(() -> getParentFragmentManager().popBackStack());
        loginView.setOnSendCodeListener(ignored -> openWhatsappOtp());
        loginView.setOnTryAnotherMethodListener(this::openFlashCall);
        return loginView;
    }

    private void openWhatsappOtp() {
        String phoneNumber = requireArguments().getString(ARG_PHONE_NUMBER, "");
        String email = requireArguments().getString(ARG_EMAIL, "");
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(com.w3n.pinggo.R.id.login_fragment_container,
                        OtpFragment.newWhatsappInstance(phoneNumber, email))
                .addToBackStack(OtpFragment.class.getSimpleName() + "_WhatsApp")
                .commit();
    }

    private void openFlashCall() {
        String phoneNumber = requireArguments().getString(ARG_PHONE_NUMBER, "");
        String email = requireArguments().getString(ARG_EMAIL, "");
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(com.w3n.pinggo.R.id.login_fragment_container,
                        FlashCallFragment.newInstance(phoneNumber, email))
                .addToBackStack(FlashCallFragment.class.getSimpleName())
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
            loginView.setOnSendCodeListener(null);
            loginView.setOnTryAnotherMethodListener(null);
        }
        loginView = null;
        super.onDestroyView();
    }
}
