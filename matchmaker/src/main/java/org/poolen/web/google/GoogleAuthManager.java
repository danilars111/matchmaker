package org.poolen.web.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.SheetsScopes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

/**
 * Manages the OAuth 2.0 authentication flow with Google.
 * This includes handling user credentials, tokens, and the authorization process.
 */
public class GoogleAuthManager {

    private static final String APPLICATION_NAME = "D&D Matchmaker Deluxe";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String CREDENTIALS_FILE_PATH = "/credentials/credentials.json";
    private static HttpTransport HTTP_TRANSPORT;

    // We keep a reference to the receiver so we can stop it if the user cancels.
    private static volatile LocalServerReceiver receiver;

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * A helper method to build the GoogleAuthorizationCodeFlow.
     * @return A configured GoogleAuthorizationCodeFlow instance.
     * @throws IOException If the credentials file cannot be found or read.
     */
    private static GoogleAuthorizationCodeFlow getFlow() throws IOException {
        InputStream in = GoogleAuthManager.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        return new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
    }

    /**
     * Authorizes the installed application to access user's protected data.
     *
     * @return A credential object.
     * @throws IOException If the credentials file cannot be found or read.
     */
    public static Credential getCredentials() throws IOException {
        GoogleAuthorizationCodeFlow flow = getFlow();
        // This is the part that starts the local server. We store it before using it.
        receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        try {
            // This is the blocking call that will open the browser.
            return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        } finally {
            // Once authorize() returns (either by success or exception), the receiver's job is done for this attempt.
            receiver = null;
        }
    }

    /**
     * Checks if there are valid, refreshable credentials on disk without triggering a sign-in.
     * @return True if valid credentials exist, false otherwise.
     */
    public static boolean hasStoredCredentials() {
        try {
            Credential credential = getFlow().loadCredential("user");
            if (credential != null) {
                // A non-null credential means a token file exists. Now, let's see if it's valid.
                // The refreshToken() method is the key. It returns true if it successfully refreshed.
                // It returns false if no refresh was needed (i.e., the token is still valid).
                // Most importantly, it throws an IOException (specifically TokenResponseException)
                // if the token is expired or revoked. This is our validation check!
                credential.refreshToken();
                // If the line above doesn't throw an exception, the credential is valid.
                return true;
            }
            // No credential file found.
            return false;
        } catch (IOException e) {
            // This block will catch the TokenResponseException for an invalid_grant.
            System.err.println("Could not validate stored credentials, likely expired or revoked: " + e.getMessage());
            // Since the token is bad, let's be a good citizen and clean it up.
            logout();
            return false;
        }
    }

    /**
     * Deletes the stored tokens, effectively logging the user out.
     */
    public static void logout() {
        try {
            File tokensDir = new File(TOKENS_DIRECTORY_PATH);
            if (tokensDir.exists()) {
                Files.deleteIfExists(Paths.get(TOKENS_DIRECTORY_PATH, "StoredCredential"));
            }
        } catch (IOException e) {
            System.err.println("Failed to delete tokens on logout: " + e.getMessage());
        }
    }

    /**
     * Stops the local server used for the OAuth flow. This should be called
     * if the user cancels the sign-in process.
     */
    public static void abortAuthorization() {
        LocalServerReceiver tempReceiver = receiver;
        if (tempReceiver != null) {
            try {
                tempReceiver.stop();
                System.out.println("Authorization receiver stopped by user cancellation.");
            } catch (IOException e) {
                System.err.println("Failed to stop authorization receiver: " + e.getMessage());
            }
        }
    }

    /**
     * Gets the shared HTTP transport.
     * @return The HttpTransport instance.
     */
    public static HttpTransport getHttpTransport() {
        return HTTP_TRANSPORT;
    }

    /**
     * Gets the shared JSON factory.
     * @return The JsonFactory instance.
     */
    public static JsonFactory getJsonFactory() {
        return JSON_FACTORY;
    }
}
