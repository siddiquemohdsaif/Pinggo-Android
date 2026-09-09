package com.w3n.pinggo.Util;

import android.content.Context;

import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;

/** Presentation-only phone formatting backed by libphonenumber. */
public final class PhoneNumberFormatter {
    private PhoneNumberFormatter() { }

    public static String formatInternational(Context context, String value) {
        String digits = digitsOnly(value);
        if (digits.isEmpty()) return "Unknown";
        try {
            PhoneNumberUtil util = PhoneNumberUtil.createInstance(context.getApplicationContext());
            Phonenumber.PhoneNumber number = util.parse("+" + digits, null);
            return util.format(number, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
        } catch (Exception ignored) {
            return "+" + digits;
        }
    }

    /** Converts a device-contact number to E.164 digits using the device country for local numbers. */
    public static String toE164Digits(Context context, String value, String defaultRegion) {
        String source = value == null ? "" : value.trim().replace("<plus>", "+");
        if (source.isEmpty()) return "";
        try {
            PhoneNumberUtil util = PhoneNumberUtil.createInstance(context.getApplicationContext());
            Phonenumber.PhoneNumber number = util.parse(source, defaultRegion);
            if (!util.isValidNumber(number)) return "";
            return digitsOnly(util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164));
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String digitsOnly(String value) {
        String source = value == null ? "" : value.trim().replace("<plus>", "+");
        StringBuilder digits = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
        }
        return digits.toString();
    }
}
