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
import com.w3n.wavestream.views.login.OtpLoginView;

/** Hosts the email verification-code step. */
public class OtpFragment extends Fragment {
    private static final String VALID_OTP = "123456";
    private static final String ARG_PHONE_NUMBER = "phone_number";
    private static final String ARG_EMAIL = "email";
    private static final String ARG_CHANNEL = "channel";
    private static final String CHANNEL_EMAIL = "email";
    private static final String CHANNEL_WHATSAPP = "whatsapp";
    private static final String CHANNEL_SMS = "sms";
    private OtpLoginView loginView;

    public static OtpFragment newInstance(@NonNull String fullPhoneNumber,
                                          @NonNull String email) {
        return newEmailInstance(fullPhoneNumber, email);
    }

    public static OtpFragment newEmailInstance(@NonNull String fullPhoneNumber,
                                               @NonNull String email) {
        return create(fullPhoneNumber, email, CHANNEL_EMAIL);
    }

    public static OtpFragment newWhatsappInstance(@NonNull String fullPhoneNumber,
                                                  @NonNull String email) {
        return create(fullPhoneNumber, email, CHANNEL_WHATSAPP);
    }

    public static OtpFragment newSmsInstance(@NonNull String fullPhoneNumber,
                                             @NonNull String email) {
        return create(fullPhoneNumber, email, CHANNEL_SMS);
    }

    private static OtpFragment create(@NonNull String fullPhoneNumber,
                                      @NonNull String email,
                                      @NonNull String channel) {
        OtpFragment fragment = new OtpFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_PHONE_NUMBER, fullPhoneNumber);
        arguments.putString(ARG_EMAIL, email);
        arguments.putString(ARG_CHANNEL, channel);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        boolean isWhatsApp = isWhatsappChannel();
        boolean isSms = isSmsChannel();
        String identifier = requireArguments().getString(
                isWhatsApp || isSms ? ARG_PHONE_NUMBER : ARG_EMAIL, "");
        loginView = new OtpLoginView(requireContext(),
                isWhatsApp ? OtpLoginView.Channel.WHATSAPP
                        : isSms ? OtpLoginView.Channel.SMS : OtpLoginView.Channel.EMAIL,
                identifier);
        loginView.setOnBackListener(() -> getParentFragmentManager().popBackStack());
        loginView.setOnOtpCompleteListener(this::verifyOtp);
        return loginView;
    }

    private void verifyOtp(String otp) {
        if (!VALID_OTP.equals(otp)) {
            if (loginView != null) loginView.showOtpError(getString(R.string.invalid_otp));
            return;
        }

        if (isWhatsappChannel()) {
            String phoneNumber = requireArguments().getString(ARG_PHONE_NUMBER, "");
            String email = requireArguments().getString(ARG_EMAIL, "");
            getParentFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.login_fragment_container,
                            FlashCallFragment.newInstance(phoneNumber, email))
                    .addToBackStack(FlashCallFragment.class.getSimpleName())
                    .commit();
            return;
        }

        if (isSmsChannel()) {
            // The next onboarding step will be connected after SMS verification.
            return;
        }

        String phoneNumber = requireArguments().getString(ARG_PHONE_NUMBER, "");
        String email = requireArguments().getString(ARG_EMAIL, "");
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.login_fragment_container,
                        WhatsappLoginFragment.newInstance(phoneNumber, email))
                .addToBackStack(WhatsappLoginFragment.class.getSimpleName())
                .commit();
    }

    private boolean isWhatsappChannel() {
        return CHANNEL_WHATSAPP.equals(
                requireArguments().getString(ARG_CHANNEL, CHANNEL_EMAIL));
    }

    private boolean isSmsChannel() {
        return CHANNEL_SMS.equals(
                requireArguments().getString(ARG_CHANNEL, CHANNEL_EMAIL));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (loginView != null) loginView.setStatusBarInset(systemBarInsets.top);
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
            loginView.setOnOtpCompleteListener(null);
        }
        loginView = null;
        super.onDestroyView();
    }
}
