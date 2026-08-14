package com.w3n.wavestream.utils.login;

import android.content.Context;
import android.os.LocaleList;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Resolves a likely country without requesting device location. */
public final class CountryDetector {
    public static final String DEFAULT_COUNTRY_ISO = "IN";

    private static final Set<String> ISO_COUNTRIES = new HashSet<>(
            Arrays.asList(Locale.getISOCountries()));

    private CountryDetector() {
    }

    /**
     * Uses the SIM country first, then the current mobile network, then the
     * device locale. India is returned when none provides a valid ISO code.
     */
    public static String detectCountryIso(Context context) {
        TelephonyManager telephonyManager =
                context.getSystemService(TelephonyManager.class);
        if (telephonyManager != null) {
            try {
                String simCountry = validIso(telephonyManager.getSimCountryIso());
                if (simCountry != null) {
                    Log.d("COUNTRY_DETECT", "detectCountryIso: simCountry ="+simCountry);
                    return simCountry;
                }
            } catch (SecurityException ignored) {
                // Continue to the next source if a vendor build restricts SIM data.
            }

            try {
                String networkCountry = validIso(telephonyManager.getNetworkCountryIso());
                if (networkCountry != null) {
                    Log.d("COUNTRY_DETECT", "detectCountryIso: networkCountry ="+networkCountry);
                    return networkCountry;
                }
            } catch (SecurityException ignored) {
                // Some vendor builds restrict network information. Continue to locale.
            }
        }

        LocaleList locales = context.getResources().getConfiguration().getLocales();
        if (!locales.isEmpty()) {
            String localeCountry = validIso(locales.get(0).getCountry());
            if (localeCountry != null) {
                Log.d("COUNTRY_DETECT", "detectCountryIso: localeCountry ="+localeCountry);
                return localeCountry;
            }

        }
        Log.d("COUNTRY_DETECT", "detectCountryIso: DEFAULT_COUNTRY_ISO ="+DEFAULT_COUNTRY_ISO);
        return DEFAULT_COUNTRY_ISO;
    }

    private static String validIso(String countryIso) {
        if (countryIso == null) return null;
        String normalized = countryIso.trim().toUpperCase(Locale.US);
        return ISO_COUNTRIES.contains(normalized) ? normalized : null;
    }
}
