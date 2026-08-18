package com.w3n.pinggo.Util.login;

import android.content.Context;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

/** Reusable Credential Manager wrapper that returns a Google ID token to its caller. */
public final class GoogleSignInManager {
    private final CredentialManager credentialManager;

    public GoogleSignInManager(@NonNull Context context) {
        credentialManager = CredentialManager.create(context.getApplicationContext());
    }

    @NonNull
    public CancellationSignal signIn(@NonNull FragmentActivity activity,
                                     @NonNull String serverClientId,
                                     @NonNull Callback callback) {
        GetSignInWithGoogleOption googleOption =
                new GetSignInWithGoogleOption.Builder(serverClientId).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build();
        CancellationSignal cancellationSignal = new CancellationSignal();

        credentialManager.getCredentialAsync(
                activity,
                request,
                cancellationSignal,
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(@NonNull GetCredentialResponse result) {
                        handleCredential(result.getCredential(), callback);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException error) {
                        if (error instanceof GetCredentialCancellationException) {
                            callback.onCancelled();
                            return;
                        }
                        callback.onError(error.getMessage());
                    }
                });
        return cancellationSignal;
    }

    private static void handleCredential(@NonNull Credential credential,
                                         @NonNull Callback callback) {
        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                .equals(credential.getType())) {
            callback.onError("Google did not return an ID token.");
            return;
        }

        try {
            GoogleIdTokenCredential googleCredential = GoogleIdTokenCredential.createFrom(
                    ((CustomCredential) credential).getData());
            String idToken = googleCredential.getIdToken();
            if (idToken == null || idToken.trim().isEmpty()) {
                callback.onError("Google returned an empty ID token.");
                return;
            }
            callback.onIdToken(idToken);
        } catch (RuntimeException error) {
            callback.onError(error.getMessage());
        }
    }

    public interface Callback {
        void onIdToken(@NonNull String idToken);

        void onCancelled();

        void onError(String message);
    }
}
