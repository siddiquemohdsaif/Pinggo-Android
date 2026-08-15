package com.w3n.wavestream.fragment.login;

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

import com.w3n.wavestream.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.wavestream.Database.CloudFunction.Utils.OtpHandler;
import com.w3n.wavestream.views.login.FlashCallLoginView;

/** Hosts the automatic flash-call verification step. */
public class FlashCallFragment extends Fragment {
    private static final String ARG_PHONE_NUMBER = "phone_number";
    private static final String ARG_EMAIL = "email";
    private FlashCallLoginView loginView;
    private boolean requestInProgress;

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
        loginView.setOnSmsAvailableListener(this::requestSmsOtp);
        return loginView;
    }

    private void requestSmsOtp() {
        if (requestInProgress) return;
        requestInProgress = true;
        String phoneNumber = requireArguments().getString(ARG_PHONE_NUMBER, "");
        AppFunctionManager.getInstance().smsSend(phoneNumber,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        requestInProgress = false;
                        if (isAdded() && object instanceof OtpHandler.OtpResult) {
                            openSmsOtp((OtpHandler.OtpResult) object);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        requestInProgress = false;
                        if (isAdded()) {
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void openSmsOtp(OtpHandler.OtpResult result) {
        String phoneNumber = requireArguments().getString(ARG_PHONE_NUMBER, "");
        String email = requireArguments().getString(ARG_EMAIL, "");
        getParentFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(com.w3n.wavestream.R.id.login_fragment_container,
                        OtpFragment.newSmsInstance(phoneNumber, email,
                                result.getReqId(), result.getProvider()))
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
        requestInProgress = false;
        super.onDestroyView();
    }
}
