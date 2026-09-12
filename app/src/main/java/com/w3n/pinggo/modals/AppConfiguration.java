package com.w3n.pinggo.modals;

import androidx.annotation.Keep;

import java.util.ArrayList;

@Keep
public class AppConfiguration {

    private LoginOption loginOption;
    private ArrayList<String> premiumCountryList;
    private String callEngine;

    public AppConfiguration() {
    }

    public AppConfiguration(LoginOption loginOption, ArrayList<String> premiumCountryList) {
        this.loginOption = loginOption;
        this.premiumCountryList = premiumCountryList;
    }

    public LoginOption getLoginOption() {
        return loginOption;
    }

    public ArrayList<String> getPremiumCountryList() {
        return premiumCountryList;
    }

    /** Returns "livekit" only when explicitly enabled by remote app configuration. */
    public String getCallEngine() {
        return "livekit".equalsIgnoreCase(callEngine) ? "livekit" : "legacy";
    }

    // LoginOption class
    @Keep
    public static class LoginOption {
        public ArrayList<String> email;
        public ArrayList<String> whatsapp;
        public ArrayList<String> flash;
        public ArrayList<String> sms;

        public ArrayList<String> getEmail() {
            return email;
        }

        public ArrayList<String> getWhatsapp() {
            return whatsapp;
        }

        public ArrayList<String> getFlash() {
            return flash;
        }

        public ArrayList<String> getSms() {
            return sms;
        }
    }


}
