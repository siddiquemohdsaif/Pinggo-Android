package com.w3n.pinggo.fragment.login;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.w3n.pinggo.AppContextProvider;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.OtpHandler;
import com.w3n.pinggo.R;
import com.w3n.pinggo.activity.HomeActivity;
import com.w3n.pinggo.activity.SignUpActivity;
import com.w3n.pinggo.utils.login.LoginFlowResolver;
import com.w3n.pinggo.views.common.BlockingProgressView;
import com.w3n.pinggo.views.login.FlashCallLoginView;

/** Hosts the automatic flash-call verification step. */
public class FlashCallFragment extends Fragment {
    private static final String ARG_PHONE_NUMBER = "phone_number";
    private static final String ARG_EMAIL = "email";
    private static final String ARG_COUNTRY_CODE = "country_code";
    private FlashCallLoginView loginView;
    private BlockingProgressView blockingProgressView;
    private boolean requestInProgress;
    private boolean smsFlowSelected;
    private final OnBackPressedCallback blockBackWhileLoading =
            new OnBackPressedCallback(false) {
                @Override
                public void handleOnBackPressed() {
                    // Authentication requests must finish before leaving this screen.
                }
            };

    public static FlashCallFragment newInstance(@NonNull String phoneNumber,
                                                @NonNull String email,
                                                @NonNull String countryCode) {
        FlashCallFragment fragment = new FlashCallFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_PHONE_NUMBER, phoneNumber);
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
        loginView = new FlashCallLoginView(requireContext(), phoneNumber);
        loginView.setOnBackListener(() -> getParentFragmentManager().popBackStack());
        loginView.setOnSmsAvailableListener(this::openNextLoginMethod);
        loginView.setOnFlashCallCompleteListener(this::loginUser);
        return loginView;
    }

    private void openNextLoginMethod() {
        String countryCode = requireArguments().getString(ARG_COUNTRY_CODE, "");
        LoginFlowResolver.LoginMethod nextMethod = LoginFlowResolver.resolveNext(
                AppContextProvider.getParsedAppConfig(), countryCode,
                LoginFlowResolver.LoginMethod.FLASH);
        if (nextMethod == LoginFlowResolver.LoginMethod.SMS) {
            requestSmsOtp();
            return;
        }
        Toast.makeText(requireContext(), R.string.no_login_option_available,
                Toast.LENGTH_LONG).show();
    }

    private void requestSmsOtp() {
        if (requestInProgress) return;
        smsFlowSelected = true;
        setRequestInProgress(true);
        String phoneNumber = requireArguments().getString(ARG_PHONE_NUMBER, "");
        AppFunctionManager.getInstance().smsSend(phoneNumber,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        setRequestInProgress(false);
                        if (isAdded() && object instanceof OtpHandler.OtpResult) {
                            openSmsOtp((OtpHandler.OtpResult) object);
                        }
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

    private void loginUser() {
        if (requestInProgress || smsFlowSelected) return;
        setRequestInProgress(true);
        String phoneNumber = requireArguments().getString(ARG_PHONE_NUMBER, "");
        String email = requireArguments().getString(ARG_EMAIL, "");
        AppFunctionManager.getInstance().userLogin(phoneNumber,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        setRequestInProgress(false);
                        if (!isAdded()) return;
                        Intent homeIntent = new Intent(requireContext(), HomeActivity.class);
                        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(homeIntent);
                        requireActivity().finish();
                    }

                    @Override
                    public void onError(String error) {
                        setRequestInProgress(false);
                        if (!isAdded()) return;
                        if (isUserNotFound(error)) {
                            Intent signUpIntent = new Intent(
                                    requireContext(), SignUpActivity.class);
                            signUpIntent.putExtra(SignUpActivity.EXTRA_PHONE_NUMBER, phoneNumber);
                            if (!email.trim().isEmpty()) {
                                signUpIntent.putExtra(SignUpActivity.EXTRA_EMAIL, email.trim());
                            }
                            startActivity(signUpIntent);
                            requireActivity().finish();
                            return;
                        }
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private static boolean isUserNotFound(String error) {
        return error != null && "No user found.".equalsIgnoreCase(error.trim());
    }

    private void setRequestInProgress(boolean inProgress) {
        requestInProgress = inProgress;
        blockBackWhileLoading.setEnabled(inProgress);
        if (blockingProgressView != null) blockingProgressView.setLoading(inProgress);
    }

    private void openSmsOtp(OtpHandler.OtpResult result) {
        String phoneNumber = requireArguments().getString(ARG_PHONE_NUMBER, "");
        String email = requireArguments().getString(ARG_EMAIL, "");
        String countryCode = requireArguments().getString(ARG_COUNTRY_CODE, "");
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(com.w3n.pinggo.R.id.login_fragment_container,
                        OtpFragment.newSmsInstance(phoneNumber, email, countryCode,
                                result.getReqId(), result.getProvider()))
                .addToBackStack(OtpFragment.class.getSimpleName() + "_Sms")
                .commit();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        blockingProgressView = requireActivity().findViewById(R.id.blocking_progress);
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), blockBackWhileLoading);
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
            loginView.setOnFlashCallCompleteListener(null);
        }
        loginView = null;
        if (blockingProgressView != null) blockingProgressView.setLoading(false);
        blockingProgressView = null;
        requestInProgress = false;
        smsFlowSelected = false;
        blockBackWhileLoading.setEnabled(false);
        super.onDestroyView();
    }
}
