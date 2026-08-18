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
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginHandler;
import com.w3n.pinggo.Database.CloudFunction.Utils.OtpHandler;
import com.w3n.pinggo.R;
import com.w3n.pinggo.Util.login.LoginFlowResolver;
import com.w3n.pinggo.views.common.BlockingProgressView;
import com.w3n.pinggo.views.login.PhoneNumberLoginView;

/** The first screen in the login flow. */
public class PhoneNumberFragment extends Fragment {
    private PhoneNumberLoginView loginView;
    private BlockingProgressView blockingProgressView;
    private boolean requestInProgress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        loginView = new PhoneNumberLoginView(requireContext());
        loginView.setOnNextListener(this::checkUserAndContinue);
        return loginView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        blockingProgressView = requireActivity().findViewById(R.id.blocking_progress);
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

    private void checkUserAndContinue(String fullPhoneNumber) {
        if (requestInProgress || loginView == null) return;
        String countryCode = loginView.getSelectedRegionCode();
        setRequestInProgress(true);
        AppFunctionManager.getInstance().checkUserExists(fullPhoneNumber,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        if (!isAdded()) return;
                        if (!(object instanceof LoginHandler.CheckUserExistsResult)) {
                            setRequestInProgress(false);
                            Toast.makeText(requireContext(), R.string.user_check_failed,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        LoginHandler.CheckUserExistsResult result =
                                (LoginHandler.CheckUserExistsResult) object;
                        String email = result.getEmail();
                        boolean emailEnabled = LoginFlowResolver.isAvailable(
                                AppContextProvider.getParsedAppConfig(), countryCode,
                                LoginFlowResolver.LoginMethod.EMAIL);
                        if (result.exists() && emailEnabled && !email.isEmpty()) {
                            sendExistingUserEmailOtp(fullPhoneNumber, countryCode, email);
                            return;
                        }

                        setRequestInProgress(false);
                        openNextLoginStep(fullPhoneNumber, countryCode);
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

    private void sendExistingUserEmailOtp(String fullPhoneNumber, String countryCode,
                                          String email) {
        AppFunctionManager.getInstance().emailSend(email,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        setRequestInProgress(false);
                        if (!isAdded()) return;
                        getParentFragmentManager()
                                .beginTransaction()
                                .setReorderingAllowed(true)
                                .replace(R.id.login_fragment_container,
                                        OtpFragment.newEmailInstance(
                                                fullPhoneNumber, email, countryCode))
                                .addToBackStack(OtpFragment.class.getSimpleName() + "_Email")
                                .commit();
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

    private void openNextLoginStep(String fullPhoneNumber, String countryCode) {
        LoginFlowResolver.LoginMethod loginMethod = LoginFlowResolver.resolve(
                AppContextProvider.getParsedAppConfig(),
                countryCode);

        switch (loginMethod) {
            case EMAIL:
                openEmailFragment(fullPhoneNumber, countryCode);
                break;
            case WHATSAPP:
                openWhatsappFragment(fullPhoneNumber, countryCode);
                break;
            case FLASH:
                openFlashCallFragment(fullPhoneNumber, countryCode);
                break;
            case SMS:
                requestSmsOtp(fullPhoneNumber, countryCode);
                break;
            default:
                Toast.makeText(requireContext(), R.string.no_login_option_available,
                        Toast.LENGTH_LONG).show();
        }
    }

    private void openEmailFragment(String fullPhoneNumber, String countryCode) {
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.login_fragment_container,
                        EmailFragment.newInstance(fullPhoneNumber, countryCode))
                .addToBackStack(EmailFragment.class.getSimpleName())
                .commit();
    }

    private void openWhatsappFragment(String fullPhoneNumber, String countryCode) {
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.login_fragment_container,
                        WhatsappLoginFragment.newInstance(fullPhoneNumber, "", countryCode))
                .addToBackStack(WhatsappLoginFragment.class.getSimpleName())
                .commit();
    }

    private void openFlashCallFragment(String fullPhoneNumber, String countryCode) {
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.login_fragment_container,
                        FlashCallFragment.newInstance(fullPhoneNumber, "", countryCode))
                .addToBackStack(FlashCallFragment.class.getSimpleName())
                .commit();
    }

    private void requestSmsOtp(String fullPhoneNumber, String countryCode) {
        setRequestInProgress(true);
        AppFunctionManager.getInstance().smsSend(fullPhoneNumber,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        setRequestInProgress(false);
                        if (!isAdded()) return;
                        if (object instanceof OtpHandler.OtpResult) {
                            OtpHandler.OtpResult result = (OtpHandler.OtpResult) object;
                            openSmsOtpFragment(fullPhoneNumber, countryCode, result);
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

    private void openSmsOtpFragment(String fullPhoneNumber, String countryCode,
                                    OtpHandler.OtpResult result) {
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.login_fragment_container,
                        OtpFragment.newSmsInstance(fullPhoneNumber, "", countryCode,
                                result.getReqId(), result.getProvider()))
                .addToBackStack(OtpFragment.class.getSimpleName() + "_Sms")
                .commit();
    }

    private void setRequestInProgress(boolean inProgress) {
        requestInProgress = inProgress;
        if (blockingProgressView != null) blockingProgressView.setLoading(inProgress);
    }

    @Override
    public void onDestroyView() {
        if (loginView != null) {
            loginView.setOnNextListener(null);
        }
        View view = getView();
        if (view != null) ViewCompat.setOnApplyWindowInsetsListener(view, null);
        loginView = null;
        if (blockingProgressView != null) blockingProgressView.setLoading(false);
        blockingProgressView = null;
        requestInProgress = false;
        super.onDestroyView();
    }
}
