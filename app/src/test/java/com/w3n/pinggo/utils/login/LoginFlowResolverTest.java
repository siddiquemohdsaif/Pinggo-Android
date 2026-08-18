package com.w3n.pinggo.utils.login;

import static org.junit.Assert.assertEquals;

import com.w3n.pinggo.Util.login.LoginFlowResolver;
import com.w3n.pinggo.modals.AppConfiguration;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class LoginFlowResolverTest {
    @Test
    public void normalCountryStartsWithEmail() {
        assertEquals(LoginFlowResolver.LoginMethod.EMAIL,
                LoginFlowResolver.resolve(configuration(), "IN"));
    }

    @Test
    public void premiumCountrySkipsNormalOnlyOptions() {
        assertEquals(LoginFlowResolver.LoginMethod.FLASH,
                LoginFlowResolver.resolve(configuration(), "US"));
    }

    @Test
    public void unitedKingdomRecognizesAndroidGbCode() {
        assertEquals(LoginFlowResolver.LoginMethod.FLASH,
                LoginFlowResolver.resolve(configuration(), "GB"));
    }

    @Test
    public void smsIsUsedWhenEarlierOptionsAreUnavailable() {
        AppConfiguration configuration = configuration();
        configuration.getLoginOption().flash = new ArrayList<>();

        assertEquals(LoginFlowResolver.LoginMethod.SMS,
                LoginFlowResolver.resolve(configuration, "US"));
    }

    @Test
    public void premiumWhatsappFallbackSkipsNormalOnlyFlash() {
        AppConfiguration configuration = configuration();
        configuration.getLoginOption().whatsapp = list("premium", "normal");
        configuration.getLoginOption().flash = list("normal");

        assertEquals(LoginFlowResolver.LoginMethod.SMS,
                LoginFlowResolver.resolveNext(configuration, "US",
                        LoginFlowResolver.LoginMethod.WHATSAPP));
    }

    @Test
    public void noFallbackIsReturnedWhenWhatsappIsOnlyPremiumOption() {
        AppConfiguration configuration = configuration();
        configuration.getLoginOption().whatsapp = list("premium", "normal");
        configuration.getLoginOption().flash = list("normal");
        configuration.getLoginOption().sms = list("normal");

        assertEquals(LoginFlowResolver.LoginMethod.NONE,
                LoginFlowResolver.resolveNext(configuration, "US",
                        LoginFlowResolver.LoginMethod.WHATSAPP));
    }

    @Test
    public void emailAvailabilityUsesCountryType() {
        AppConfiguration configuration = configuration();

        assertEquals(true, LoginFlowResolver.isAvailable(configuration, "IN",
                LoginFlowResolver.LoginMethod.EMAIL));
        assertEquals(false, LoginFlowResolver.isAvailable(configuration, "US",
                LoginFlowResolver.LoginMethod.EMAIL));
    }

    private static AppConfiguration configuration() {
        AppConfiguration.LoginOption options = new AppConfiguration.LoginOption();
        options.email = list("normal");
        options.whatsapp = list("normal");
        options.flash = list("premium", "normal");
        options.sms = list("premium", "normal");
        return new AppConfiguration(options, list("US", "UK", "LU"));
    }

    private static ArrayList<String> list(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }
}
