package com.w3n.pinggo.fragment.login;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.w3n.pinggo.AppContextProvider;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.OtpHandler;
import com.w3n.pinggo.R;
import com.w3n.pinggo.utils.login.LoginFlowResolver;
import com.w3n.pinggo.views.common.BlockingProgressView;
import com.w3n.pinggo.views.login.WhatsappLoginView;

/** The WhatsApp login step reached after successful email OTP verification. */
public class WhatsappLoginFragment extends Fragment {
    private static final String ARG_PHONE_NUMBER = "phone_number";
    private static final String ARG_EMAIL = "email";
    private static final String ARG_COUNTRY_CODE = "country_code";
    private WhatsappLoginView loginView;
    private BlockingProgressView blockingProgressView;
    private boolean requestInProgress;

    public static WhatsappLoginFragment newInstance(@NonNull String fullPhoneNumber,
                                                     @NonNull String email,
                                                     @NonNull String countryCode) {
        WhatsappLoginFragment fragment = new WhatsappLoginFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_PHONE_NUMBER, fullPhoneNumber);
        arguments.putString(ARG_EMAIL, email);
        arguments.putString(ARG_COUNTRY_CODE, countryCode);
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
        loginView.setOnTryAnotherMethodListener(this::openNextLoginMethod);
        return loginView;
    }

    private void openWhatsappOtp() {
        String phoneNumber = requireArguments().getString(ARG_PHONE_NUMBER, "");
        String email = requireArguments().getString(ARG_EMAIL, "");
        String countryCode = requireArguments().getString(ARG_COUNTRY_CODE, "");
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(com.w3n.pinggo.R.id.login_fragment_container,
                        OtpFragment.newWhatsappInstance(phoneNumber, email, countryCode))
                .addToBackStack(OtpFragment.class.getSimpleName() + "_WhatsApp")
                .commit();
    }

    private void openNextLoginMethod() {
        if (requestInProgress) return;
        String phoneNumber = requireArguments().getString(ARG_PHONE_NUMBER, "");
        String email = requireArguments().getString(ARG_EMAIL, "");
        String countryCode = requireArguments().getString(ARG_COUNTRY_CODE, "");
        LoginFlowResolver.LoginMethod nextMethod = LoginFlowResolver.resolveNext(
                AppContextProvider.getParsedAppConfig(), countryCode,
                LoginFlowResolver.LoginMethod.WHATSAPP);
        switch (nextMethod) {
            case FLASH:
                getParentFragmentManager()
                        .beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.login_fragment_container,
                                FlashCallFragment.newInstance(
                                        phoneNumber, email, countryCode))
                        .addToBackStack(FlashCallFragment.class.getSimpleName())
                        .commit();
                break;
            case SMS:
                requestSmsOtp(phoneNumber, email, countryCode);
                break;
            default:
                Toast.makeText(requireContext(), R.string.no_login_option_available,
                        Toast.LENGTH_LONG).show();
        }
    }

    private void requestSmsOtp(String phoneNumber, String email, String countryCode) {
        setRequestInProgress(true);
        AppFunctionManager.getInstance().smsSend(phoneNumber,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        setRequestInProgress(false);
                        if (!isAdded()) return;
                        if (object instanceof OtpHandler.OtpResult) {
                            OtpHandler.OtpResult result = (OtpHandler.OtpResult) object;
                            getParentFragmentManager()
                                    .beginTransaction()
                                    .setReorderingAllowed(true)
                                    .replace(R.id.login_fragment_container,
                                            OtpFragment.newSmsInstance(phoneNumber, email,
                                                    countryCode, result.getReqId(),
                                                    result.getProvider()))
                                    .addToBackStack(OtpFragment.class.getSimpleName() + "_Sms")
                                    .commit();
                            return;
                        }
                        Toast.makeText(requireContext(), R.string.sms_request_failed,
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String error) {
                        setRequestInProgress(false);
                        if (isAdded()) {
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void setRequestInProgress(boolean inProgress) {
        requestInProgress = inProgress;
        if (blockingProgressView != null) blockingProgressView.setLoading(inProgress);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        blockingProgressView = requireActivity().findViewById(R.id.blocking_progress);
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
        if (blockingProgressView != null) blockingProgressView.setLoading(false);
        blockingProgressView = null;
        requestInProgress = false;
        super.onDestroyView();
    }
}
