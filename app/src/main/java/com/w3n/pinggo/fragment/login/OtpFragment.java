package com.w3n.pinggo.fragment.login;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
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
import com.w3n.pinggo.Util.login.LoginFlowResolver;
import com.w3n.pinggo.activity.HomeActivity;
import com.w3n.pinggo.activity.SignUpActivity;
import com.w3n.pinggo.views.common.BlockingProgressView;
import com.w3n.pinggo.views.login.OtpLoginView;

/** Hosts the email verification-code step. */
public class OtpFragment extends Fragment {
  private static final String VALID_OTP = "123456";
  private static final String ARG_PHONE_NUMBER = "phone_number";
  private static final String ARG_EMAIL = "email";
  private static final String ARG_COUNTRY_CODE = "country_code";
  private static final String ARG_CHANNEL = "channel";
  private static final String ARG_SMS_REQ_ID = "sms_req_id";
  private static final String ARG_SMS_PROVIDER = "sms_provider";
  private static final String CHANNEL_EMAIL = "email";
  private static final String CHANNEL_WHATSAPP = "whatsapp";
  private static final String CHANNEL_SMS = "sms";
  private OtpLoginView loginView;
  private BlockingProgressView blockingProgressView;
  private boolean requestInProgress;
  private String smsReqId = "";
  private String smsProvider = "";
  private final OnBackPressedCallback blockBackWhileLoading =
      new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
          // Authentication requests must finish before leaving this screen.
        }
      };

  public static OtpFragment newInstance(
      @NonNull String fullPhoneNumber, @NonNull String email, @NonNull String countryCode) {
    return newEmailInstance(fullPhoneNumber, email, countryCode);
  }

  public static OtpFragment newEmailInstance(
      @NonNull String fullPhoneNumber, @NonNull String email, @NonNull String countryCode) {
    return create(fullPhoneNumber, email, countryCode, CHANNEL_EMAIL);
  }

  public static OtpFragment newWhatsappInstance(
      @NonNull String fullPhoneNumber, @NonNull String email, @NonNull String countryCode) {
    return create(fullPhoneNumber, email, countryCode, CHANNEL_WHATSAPP);
  }

  public static OtpFragment newSmsInstance(
      @NonNull String fullPhoneNumber, @NonNull String email, @NonNull String countryCode) {
    return create(fullPhoneNumber, email, countryCode, CHANNEL_SMS);
  }

  public static OtpFragment newSmsInstance(
      @NonNull String fullPhoneNumber,
      @NonNull String email,
      @NonNull String countryCode,
      @NonNull String reqId,
      @NonNull String provider) {
    OtpFragment fragment = create(fullPhoneNumber, email, countryCode, CHANNEL_SMS);
    Bundle arguments = fragment.getArguments();
    if (arguments != null) {
      arguments.putString(ARG_SMS_REQ_ID, reqId);
      arguments.putString(ARG_SMS_PROVIDER, provider);
    }
    return fragment;
  }

  private static OtpFragment create(
      @NonNull String fullPhoneNumber,
      @NonNull String email,
      @NonNull String countryCode,
      @NonNull String channel) {
    OtpFragment fragment = new OtpFragment();
    Bundle arguments = new Bundle();
    arguments.putString(ARG_PHONE_NUMBER, fullPhoneNumber);
    arguments.putString(ARG_EMAIL, email);
    arguments.putString(ARG_COUNTRY_CODE, countryCode);
    arguments.putString(ARG_CHANNEL, channel);
    fragment.setArguments(arguments);
    return fragment;
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    boolean isWhatsApp = isWhatsappChannel();
    boolean isSms = isSmsChannel();
    smsReqId = requireArguments().getString(ARG_SMS_REQ_ID, "");
    smsProvider = requireArguments().getString(ARG_SMS_PROVIDER, "");
    String identifier =
        requireArguments().getString(isWhatsApp || isSms ? ARG_PHONE_NUMBER : ARG_EMAIL, "");
    loginView =
        new OtpLoginView(
            requireContext(),
            isWhatsApp
                ? OtpLoginView.Channel.WHATSAPP
                : isSms ? OtpLoginView.Channel.SMS : OtpLoginView.Channel.EMAIL,
            identifier);
    loginView.setOnBackListener(() -> getParentFragmentManager().popBackStack());
    loginView.setOnOtpCompleteListener(this::verifyOtp);
    loginView.setOnResendListener(this::resendOtp);
    if (isWhatsApp) {
      loginView.setOnTryAnotherMethodListener(this::openNextLoginMethod);
    }
    return loginView;
  }

  private void verifyOtp(String otp) {
    if (requestInProgress) return;

    if (isWhatsappChannel()) {
      if (!VALID_OTP.equals(otp)) {
        if (loginView != null) loginView.showOtpError(getString(R.string.invalid_otp));
        return;
      }
      handleVerifiedOtp();
      return;
    }

    setRequestInProgress(true);
    AppFunctionManager manager = AppFunctionManager.getInstance();
    if (isSmsChannel()) {
      if (smsReqId.isEmpty()) {
        setRequestInProgress(false);
        if (loginView != null) {
          loginView.showOtpError(getString(R.string.otp_request_first));
        }
        return;
      }
      manager.smsVerify(smsReqId, smsProvider, otp, verificationCallback());
      return;
    }

    String email = requireArguments().getString(ARG_EMAIL, "");
    manager.emailVerify(email, otp, verificationCallback());
  }

  private AppFunctionManager.Callback verificationCallback() {
    return new AppFunctionManager.Callback() {
      @Override
      public void onSuccess(Object object) {
        setRequestInProgress(false);
        if (isAdded() && loginView != null) handleVerifiedOtp();
      }

      @Override
      public void onError(String error) {
        setRequestInProgress(false);
        if (loginView != null) loginView.showOtpError(error);
      }
    };
  }

  private void resendOtp() {
    if (requestInProgress) return;
    if (isWhatsappChannel()) {
      if (loginView != null) {
        loginView.showOtpError("WhatsApp resend is not available.");
      }
      return;
    }

    setRequestInProgress(true);
    AppFunctionManager manager = AppFunctionManager.getInstance();
    if (isSmsChannel()) {
      if (smsReqId.isEmpty()) {
        setRequestInProgress(false);
        String phoneNumber = requireArguments().getString(ARG_PHONE_NUMBER, "");
        manager.smsSend(phoneNumber, resendCallback(true));
        return;
      }
      manager.smsResend(smsReqId, smsProvider, resendCallback(true));
      return;
    }

    String email = requireArguments().getString(ARG_EMAIL, "");
    manager.emailResend(email, resendCallback(false));
  }

  private AppFunctionManager.Callback resendCallback(boolean sms) {
    return new AppFunctionManager.Callback() {
      @Override
      public void onSuccess(Object object) {
        setRequestInProgress(false);
        if (sms && object instanceof OtpHandler.OtpResult) {
          OtpHandler.OtpResult result = (OtpHandler.OtpResult) object;
          if (!result.getReqId().isEmpty()) smsReqId = result.getReqId();
          if (!result.getProvider().isEmpty()) smsProvider = result.getProvider();
        }
        if (loginView != null) loginView.resetAfterResend();
      }

      @Override
      public void onError(String error) {
        setRequestInProgress(false);
        if (loginView != null) loginView.showOtpError(error);
      }
    };
  }

  private void handleVerifiedOtp() {
    if (isWhatsappChannel() || isSmsChannel()) {
      loginUser();
      return;
    }

    openNextLoginMethod();
  }

  private void openNextLoginMethod() {
    String phoneNumber = requireArguments().getString(ARG_PHONE_NUMBER, "");
    String email = requireArguments().getString(ARG_EMAIL, "");
    String countryCode = requireArguments().getString(ARG_COUNTRY_CODE, "");
    LoginFlowResolver.LoginMethod currentMethod =
        isWhatsappChannel()
            ? LoginFlowResolver.LoginMethod.WHATSAPP
            : LoginFlowResolver.LoginMethod.EMAIL;
    LoginFlowResolver.LoginMethod nextMethod =
        LoginFlowResolver.resolveNext(
            AppContextProvider.getParsedAppConfig(), countryCode, currentMethod);
    switch (nextMethod) {
      case WHATSAPP:
        getParentFragmentManager()
            .beginTransaction()
            .setReorderingAllowed(true)
            .replace(
                R.id.login_fragment_container,
                WhatsappLoginFragment.newInstance(phoneNumber, email, countryCode))
            .addToBackStack(WhatsappLoginFragment.class.getSimpleName())
            .commit();
        break;
      case FLASH:
        getParentFragmentManager()
            .beginTransaction()
            .setReorderingAllowed(true)
            .replace(
                R.id.login_fragment_container,
                FlashCallFragment.newInstance(phoneNumber, email, countryCode))
            .addToBackStack(FlashCallFragment.class.getSimpleName())
            .commit();
        break;
      case SMS:
        requestNextSmsOtp(phoneNumber, email, countryCode);
        break;
      default:
        Toast.makeText(requireContext(), R.string.no_login_option_available, Toast.LENGTH_LONG)
            .show();
    }
  }

  private void requestNextSmsOtp(String phoneNumber, String email, String countryCode) {
    setRequestInProgress(true);
    AppFunctionManager.getInstance()
        .smsSend(
            phoneNumber,
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
                      .replace(
                          R.id.login_fragment_container,
                          OtpFragment.newSmsInstance(
                              phoneNumber,
                              email,
                              countryCode,
                              result.getReqId(),
                              result.getProvider()))
                      .addToBackStack(OtpFragment.class.getSimpleName() + "_Sms")
                      .commit();
                  return;
                }
                Toast.makeText(requireContext(), R.string.sms_request_failed, Toast.LENGTH_LONG)
                    .show();
              }

              @Override
              public void onError(String error) {
                setRequestInProgress(false);
                if (isAdded() && loginView != null) loginView.showOtpError(error);
              }
            });
  }

  private void loginUser() {
    if (requestInProgress) return;
    setRequestInProgress(true);
    String phoneNumber = requireArguments().getString(ARG_PHONE_NUMBER, "");
    String email = requireArguments().getString(ARG_EMAIL, "");
    AppFunctionManager.getInstance()
        .userLogin(
            phoneNumber,
            new AppFunctionManager.Callback() {
              @Override
              public void onSuccess(Object object) {
                setRequestInProgress(false);
                if (!isAdded()) return;
                Intent homeIntent = new Intent(requireContext(), HomeActivity.class);
                homeIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(homeIntent);
                requireActivity().finish();
              }

              @Override
              public void onError(String error) {
                setRequestInProgress(false);
                if (!isAdded()) return;
                if (isUserNotFound(error)) {
                  Intent signUpIntent = new Intent(requireContext(), SignUpActivity.class);
                  signUpIntent.putExtra(SignUpActivity.EXTRA_PHONE_NUMBER, phoneNumber);
                  if (!email.trim().isEmpty()) {
                    signUpIntent.putExtra(SignUpActivity.EXTRA_EMAIL, email.trim());
                  }
                  startActivity(signUpIntent);
                  requireActivity().finish();
                  return;
                }
                if (loginView != null) loginView.showOtpError(error);
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

  private boolean isWhatsappChannel() {
    return CHANNEL_WHATSAPP.equals(requireArguments().getString(ARG_CHANNEL, CHANNEL_EMAIL));
  }

  private boolean isSmsChannel() {
    return CHANNEL_SMS.equals(requireArguments().getString(ARG_CHANNEL, CHANNEL_EMAIL));
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    blockingProgressView = requireActivity().findViewById(R.id.blocking_progress);
    requireActivity()
        .getOnBackPressedDispatcher()
        .addCallback(getViewLifecycleOwner(), blockBackWhileLoading);
    ViewCompat.setOnApplyWindowInsetsListener(
        view,
        (target, windowInsets) -> {
          Insets systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
          Insets navigationBarInsets =
              windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
          if (loginView != null) {
            loginView.setInsets(systemBarInsets.top, navigationBarInsets.bottom);
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
      loginView.setOnOtpCompleteListener(null);
      loginView.setOnResendListener(null);
      loginView.setOnTryAnotherMethodListener(null);
    }
    loginView = null;
    if (blockingProgressView != null) blockingProgressView.setLoading(false);
    blockingProgressView = null;
    requestInProgress = false;
    blockBackWhileLoading.setEnabled(false);
    super.onDestroyView();
  }
}
