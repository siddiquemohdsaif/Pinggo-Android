package com.w3n.pinggo.utils.login;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.w3n.pinggo.modals.AppConfiguration;

import java.util.List;
import java.util.Locale;

public final class LoginFlowResolver {
    public enum LoginMethod {
        EMAIL,
        WHATSAPP,
        FLASH,
        SMS,
        NONE
    }

    private static final String NORMAL = "normal";
    private static final String PREMIUM = "premium";

    private LoginFlowResolver() {
    }

    @NonNull
    public static LoginMethod resolve(@Nullable AppConfiguration configuration,
                                      @Nullable String selectedCountryCode) {
        return resolveNext(configuration, selectedCountryCode, null);
    }

    @NonNull
    public static LoginMethod resolveNext(@Nullable AppConfiguration configuration,
                                          @Nullable String selectedCountryCode,
                                          @Nullable LoginMethod currentMethod) {
        if (configuration == null || configuration.getLoginOption() == null) {
            return LoginMethod.NONE;
        }

        String countryType = isPremiumCountry(configuration.getPremiumCountryList(),
                selectedCountryCode) ? PREMIUM : NORMAL;
        AppConfiguration.LoginOption options = configuration.getLoginOption();
        LoginMethod[] sequence = {
                LoginMethod.EMAIL,
                LoginMethod.WHATSAPP,
                LoginMethod.FLASH,
                LoginMethod.SMS
        };
        boolean currentMethodReached = currentMethod == null;
        for (LoginMethod method : sequence) {
            if (!currentMethodReached) {
                currentMethodReached = method == currentMethod;
                continue;
            }
            if (supports(options, method, countryType)) return method;
        }
        return LoginMethod.NONE;
    }

    public static boolean isAvailable(@Nullable AppConfiguration configuration,
                                      @Nullable String selectedCountryCode,
                                      @NonNull LoginMethod method) {
        if (configuration == null || configuration.getLoginOption() == null) return false;
        String countryType = isPremiumCountry(configuration.getPremiumCountryList(),
                selectedCountryCode) ? PREMIUM : NORMAL;
        return supports(configuration.getLoginOption(), method, countryType);
    }

    private static boolean supports(@NonNull AppConfiguration.LoginOption options,
                                    @NonNull LoginMethod method,
                                    @NonNull String countryType) {
        switch (method) {
            case EMAIL:
                return supports(options.getEmail(), countryType);
            case WHATSAPP:
                return supports(options.getWhatsapp(), countryType);
            case FLASH:
                return supports(options.getFlash(), countryType);
            case SMS:
                return supports(options.getSms(), countryType);
            default:
                return false;
        }
    }

    private static boolean isPremiumCountry(@Nullable List<String> premiumCountries,
                                            @Nullable String selectedCountryCode) {
        if (premiumCountries == null || selectedCountryCode == null) return false;
        String selectedCode = normalizeCountryCode(selectedCountryCode);
        for (String premiumCountry : premiumCountries) {
            if (selectedCode.equals(normalizeCountryCode(premiumCountry))) return true;
        }
        return false;
    }

    private static boolean supports(@Nullable List<String> supportedCountryTypes,
                                    @NonNull String countryType) {
        if (supportedCountryTypes == null) return false;
        for (String supportedType : supportedCountryTypes) {
            if (countryType.equalsIgnoreCase(supportedType == null
                    ? "" : supportedType.trim())) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String normalizeCountryCode(@Nullable String countryCode) {
        String normalized = countryCode == null
                ? "" : countryCode.trim().toUpperCase(Locale.US);
        return "GB".equals(normalized) ? "UK" : normalized;
    }
}
