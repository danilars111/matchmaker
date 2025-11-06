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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(GoogleAuthManager.class);

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String CREDENTIALS_FILE_PATH = "/credentials/credentials.json";
    private static HttpTransport HTTP_TRANSPORT;

    private static volatile LocalServerReceiver receiver;

    static {
        logger.info("Setting java.awt.headless=false for Google Auth local server.");
        System.setProperty("java.awt.headless", "false");
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            logger.info("GoogleNetHttpTransport initialised successfully.");
        } catch (GeneralSecurityException | IOException e) {
            logger.error("FATAL: Failed to initialise GoogleNetHttpTransport. Application may not function.", e);
            System.exit(1);
        }
    }

    private static GoogleAuthorizationCodeFlow getFlow() throws IOException {
        logger.debug("Creating new GoogleAuthorizationCodeFlow.");
        InputStream in = GoogleAuthManager.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            logger.error("FATAL: credentials.json file not found at resource path: {}", CREDENTIALS_FILE_PATH);
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        Path tokensPath = AppDataHandler.getAppDataDirectory().resolve("tokens");
        logger.debug("Setting token data store factory to path: {}", tokensPath);

        return new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(tokensPath.toFile()))
                .setAccessType("offline")
                .build();
    }

    /**
     * This is for a brand new login, darling! It starts the whole party.
     */
    public static Credential getCredentials(Consumer<String> urlDisplayer) throws IOException {
        logger.info("Initiating new user credential authorization flow.");
        GoogleAuthorizationCodeFlow flow = getFlow();
        receiver = new LocalServerReceiver.Builder().setPort(0).build();
        try {
            String redirectUri = receiver.getRedirectUri();
            String url = flow.newAuthorizationUrl().setRedirectUri(redirectUri).build();
            logger.info("Authorization URL generated. Displaying to user and attempting to open browser.");
            urlDisplayer.accept(url);

            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                logger.debug("Successfully opened default browser.");
            } catch (Exception e) {
                logger.warn("Automatic browser opening failed. User must manually copy the link.", e);
            }

            logger.info("Waiting for user to complete authorization in browser...");
            String code = receiver.waitForCode();
            logger.info("Authorization code received. Requesting new token.");
            TokenResponse response = flow.newTokenRequest(code).setRedirectUri(redirectUri).execute();
            logger.info("Token response received. Storing credential for 'user'.");
            return flow.createAndStoreCredential(response, "user");
        } finally {
            if (receiver != null) {
                logger.debug("Stopping local server receiver.");
                receiver.stop();
                receiver = null;
            }
        }
    }

    /**
     * Our beautiful new helper just for loading what's already there!
     */
    public static Credential loadStoredCredential() throws IOException {
        logger.debug("Attempting to load stored credential for 'user'.");
        return getFlow().loadCredential("user");
    }

    public static boolean hasStoredCredentials() {
        logger.info("Checking for stored credentials...");
        try {
            Credential credential = getFlow().loadCredential("user");
            if (credential != null) {
                logger.info("Stored credential found. Attempting to refresh token...");
                if (credential.refreshToken()) {
                    logger.info("Token refresh successful. Stored credentials are valid.");
                    return true;
                } else {
                    logger.warn("Stored credential found, but token refresh failed. Credentials may be expired or revoked.");
                    logout(); // Clean up the bad credentials
                    return false;
                }
            }
            logger.info("No stored credential found for 'user'.");
            return false;
        } catch (IOException e) {
            logger.warn("Could not validate stored credentials, likely expired or revoked. Logging out.", e);
            logout();
            return false;
        }
    }

    public static void logout() {
        logger.info("Attempting to log out by deleting stored credentials.");
        try {
            Path tokensDir = AppDataHandler.getAppDataDirectory().resolve("tokens");
            if (Files.exists(tokensDir)) {
                Path credentialFile = tokensDir.resolve("StoredCredential");
                if (Files.exists(credentialFile)) {
                    Files.delete(credentialFile);
                    logger.info("Successfully deleted 'StoredCredential' file.");
                } else {
                    logger.debug("Logout called, but 'StoredCredential' file does not exist.");
                }
            } else {
                logger.debug("Logout called, but 'tokens' directory does not exist.");
            }
        } catch (IOException e) {
            logger.error("Failed to delete tokens on logout.", e);
        }
    }

    public static void abortAuthorization() {
        logger.warn("User aborted authorization flow. Attempting to stop local server receiver.");
        LocalServerReceiver tempReceiver = receiver;
        if (tempReceiver != null) {
            try {
                tempReceiver.stop();
                logger.info("Authorization receiver stopped successfully by user cancellation.");
            } catch (IOException e) {
                logger.error("Failed to stop authorization receiver during abort.", e);
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
