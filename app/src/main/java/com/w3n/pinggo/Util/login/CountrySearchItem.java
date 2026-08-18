package com.w3n.pinggo.Util.login;

import com.hbb20.CCPCountry;

import java.util.Locale;

/** Precomputed searchable text for a country-picker row. */
public final class CountrySearchItem {
    final CCPCountry country;
    final String searchableText;

    public CountrySearchItem(CCPCountry country) {
        this.country = country;
        String phoneCode = country.getPhoneCode() == null ? "" : country.getPhoneCode();
        searchableText = lower(country.getName()) + '\n'
                + lower(country.getEnglishName()) + '\n'
                + lower(country.getNameCode()) + '\n'
                + phoneCode + '\n'
                + '+' + phoneCode;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }
}
