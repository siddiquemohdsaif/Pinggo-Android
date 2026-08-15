package com.w3n.pinggo.fragment.login;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.GoogleAuthHandler;
import com.w3n.pinggo.R;
import com.w3n.pinggo.utils.login.GoogleSignInManager;
import com.w3n.pinggo.views.common.BlockingProgressView;
import com.w3n.pinggo.views.login.EmailLoginView;

/** Collects the email after the phone-number step has been validated. */
public class EmailFragment extends Fragment {
    private static final String ARG_PHONE_NUMBER = "phone_number";
    private EmailLoginView loginView;
    private BlockingProgressView blockingProgressView;
    private GoogleSignInManager googleSignInManager;
    private CancellationSignal googleSignInCancellation;
    private boolean requestInProgress;
    private final OnBackPressedCallback blockBackWhileLoading =
            new OnBackPressedCallback(false) {
                @Override
                public void handleOnBackPressed() {
                    // The OTP request cannot be cancelled by leaving this screen.
                }
            };
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
        loginView.setOnNextListener(this::sendEmailOtp);
        loginView.setOnGoogleListener(this::signInWithGoogle);
        return loginView;
    }

    private void signInWithGoogle() {
        if (requestInProgress) return;
        setRequestInProgress(true);
        googleSignInManager = new GoogleSignInManager(requireContext());
        googleSignInCancellation = googleSignInManager.signIn(
                requireActivity(),
                getString(R.string.google_web_client_id),
                new GoogleSignInManager.Callback() {
                    @Override
                    public void onIdToken(@NonNull String idToken) {
                        verifyGoogleIdToken(idToken);
                    }

                    @Override
                    public void onCancelled() {
                        setRequestInProgress(false);
                    }

                    @Override
                    public void onError(String message) {
                        setRequestInProgress(false);
                        showGoogleError(message);
                    }
                });
    }

    private void verifyGoogleIdToken(@NonNull String idToken) {
        AppFunctionManager.getInstance().verifyGoogleIdToken(
                idToken, new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        setRequestInProgress(false);
                        if (!(object instanceof GoogleAuthHandler.VerifiedGoogleAccount)
                                || !isAdded() || loginView == null) {
                            showGoogleError(getString(R.string.google_sign_in_failed));
                            return;
                        }
                        GoogleAuthHandler.VerifiedGoogleAccount account =
                                (GoogleAuthHandler.VerifiedGoogleAccount) object;
                        loginView.setEmail(account.getEmail());
                        openWhatsappFragment(loginView.getFullPhoneNumber(), account.getEmail());
                    }

                    @Override
                    public void onError(String error) {
                        setRequestInProgress(false);
                        showGoogleError(error);
                    }
                });
    }

    private void showGoogleError(String message) {
        if (!isAdded() || loginView == null) return;
        String safeMessage = message == null || message.trim().isEmpty()
                ? getString(R.string.google_sign_in_failed) : message;
        loginView.showEmailError(safeMessage);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        blockingProgressView = requireActivity().findViewById(R.id.blocking_progress);
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), blockBackWhileLoading);
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

    private void sendEmailOtp(String fullPhoneNumber, String email) {
        if (requestInProgress) return;
        setRequestInProgress(true);
        AppFunctionManager.getInstance().emailSend(email, new AppFunctionManager.Callback() {
            @Override
            public void onSuccess(Object object) {
                setRequestInProgress(false);
                if (isAdded() && loginView != null) openOtpFragment(fullPhoneNumber, email);
            }

            @Override
            public void onError(String error) {
                setRequestInProgress(false);
                if (loginView != null) loginView.showEmailError(error);
            }
        });
    }

    private void setRequestInProgress(boolean inProgress) {
        requestInProgress = inProgress;
        blockBackWhileLoading.setEnabled(inProgress);
        if (inProgress && loginView != null) loginView.dismissKeyboard();
        if (blockingProgressView != null) blockingProgressView.setLoading(inProgress);
    }

    private void openOtpFragment(String fullPhoneNumber, String email) {
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(com.w3n.pinggo.R.id.login_fragment_container,
                        OtpFragment.newInstance(fullPhoneNumber, email))
                .addToBackStack(OtpFragment.class.getSimpleName())
                .commit();
    }

    private void openWhatsappFragment(String fullPhoneNumber, String email) {
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.login_fragment_container,
                        WhatsappLoginFragment.newInstance(fullPhoneNumber, email))
                .addToBackStack(WhatsappLoginFragment.class.getSimpleName())
                .commit();
    }

    @Override
    public void onDestroyView() {
        if (googleSignInCancellation != null) googleSignInCancellation.cancel();
        googleSignInCancellation = null;
        googleSignInManager = null;
        View view = getView();
        if (view != null) ViewCompat.setOnApplyWindowInsetsListener(view, null);
        if (loginView != null) {
            loginView.setOnNextListener(null);
            loginView.setOnBackListener(null);
            loginView.setOnGoogleListener(null);
        }
        loginView = null;
        if (blockingProgressView != null) blockingProgressView.setLoading(false);
        blockingProgressView = null;
        requestInProgress = false;
        blockBackWhileLoading.setEnabled(false);
        super.onDestroyView();
    }
}
