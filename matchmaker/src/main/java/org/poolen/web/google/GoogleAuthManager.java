package org.poolen.web.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.SheetsScopes;
import org.poolen.util.AppDataHandler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class GoogleAuthManager {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String CREDENTIALS_FILE_PATH = "/credentials/credentials.json";
    private static HttpTransport HTTP_TRANSPORT;

    private static volatile LocalServerReceiver receiver;

    static {
        // This is our lovely little instruction to the JVM!
        // It tells it to never run in "headless" mode, ensuring it's always ready for GUI tasks.
        // Not hacky at all, shut up!
        System.setProperty("java.awt.headless", "false");
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static GoogleAuthorizationCodeFlow getFlow() throws IOException {
        InputStream in = GoogleAuthManager.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        Path tokensPath = AppDataHandler.getAppDataDirectory().resolve("tokens");

        return new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(tokensPath.toFile()))
                .setAccessType("offline")
                .build();
    }

    public static Credential getCredentials(Consumer<String> urlDisplayer) throws IOException {
        GoogleAuthorizationCodeFlow flow = getFlow();
        // We set the port to 0 to tell it to find any available port, just like a clever little social butterfly!
        receiver = new LocalServerReceiver.Builder().setPort(0).build();
        try {
            String redirectUri = receiver.getRedirectUri();
            String url = flow.newAuthorizationUrl().setRedirectUri(redirectUri).build();

            // Here's the magic! We give the URL to our little helper.
            urlDisplayer.accept(url);

            // Then we try to be a good girl and open the browser automatically.
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            } catch (Exception e) {
                System.err.println("Automatic browser opening failed. Please use the provided link. Error: " + e);
            }

            // Now we wait for the user to do their thing.
            String code = receiver.waitForCode();
            TokenResponse response = flow.newTokenRequest(code).setRedirectUri(redirectUri).execute();
            return flow.createAndStoreCredential(response, "user");
        } finally {
            if (receiver != null) {
                receiver.stop();
                receiver = null;
            }
        }
    }
    public static boolean hasStoredCredentials() {
        try {
            Credential credential = getFlow().loadCredential("user");
            if (credential != null) {
                credential.refreshToken();
                return true;
            }
            return false;
        } catch (IOException e) {
            System.err.println("Could not validate stored credentials, likely expired or revoked: " + e.getMessage());
            logout();
            return false;
        }
    }

    public static void logout() {
        try {
            Path tokensDir = AppDataHandler.getAppDataDirectory().resolve("tokens");
            if (Files.exists(tokensDir)) {
                Files.deleteIfExists(tokensDir.resolve("StoredCredential"));
            }
        } catch (IOException e) {
            System.err.println("Failed to delete tokens on logout: " + e.getMessage());
        }
    }

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

    public static HttpTransport getHttpTransport() {
        return HTTP_TRANSPORT;
    }

    public static JsonFactory getJsonFactory() {
        return JSON_FACTORY;
    }
}

