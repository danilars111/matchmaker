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
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.SheetsScopes;
import org.poolen.util.AppDataHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

/**
 * Manages Google OAuth2 authentication and credential storage.
 *
 * Intended to be used as a Spring-managed singleton bean (@Component).
 *
 * REFACTOR NOTES:
 * 1. Converted from a static utility class to an instance-based manager. This encapsulates
 * state (like `receiver` and `timedOut`) within an instance, preventing global state
 * conflicts and making the class easier to manage and test.
 * 2. The `HttpTransport` and `GoogleAuthorizationCodeFlow` are now final instance fields,
 * initialized once in the constructor, improving efficiency and consistency.
 * 3. `getCredentials` renamed to `authorizeNewUser` to be more descriptive.
 * 4. `LocalServerReceiver` now uses `setPort(0)` to automatically find a free port,
 * making it more robust and less likely to fail on port conflicts.
 * 5. `hasStoredCredentials` replaced with `loadAndValidateStoredCredential`, which
 * has a clearer name, returns the `Credential` on success, and handles logout
 * on validation failure.
 * 6. `logout` now uses `flow.getDataStore().delete("user")` which is the correct,
 * robust way to delete a stored credential, rather than manual file deletion.
 * 7. `loadStoredCredential` is now a private helper method.
 * 8. `AccessDeniedException` kept as a useful custom exception.
 */
@Component
public class GoogleAuthManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleAuthManager.class);

    // --- Constants ---
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String CREDENTIALS_FILE_PATH = "/credentials/credentials.json";
    private static final long AUTH_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

    // --- Instance Fields ---
    private final HttpTransport httpTransport;
    private final GoogleAuthorizationCodeFlow flow;
    private final Path tokensPath;
    private final DataStoreFactory dataStoreFactory;

    /**
     * This holds the state for an *in-progress* authorization attempt.
     * It is volatile to ensure visibility between the auth thread and the timeout/abort thread.
     */
    private volatile LocalServerReceiver receiver;
    private volatile boolean timedOut = false;
    /**
     * Custom exception for when the user explicitly denies access in the Google consent screen.
     */
    public static class AccessDeniedException extends IOException {
        public AccessDeniedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Custom exception for when the login times out.
     */
    public static class AuthorizationTimeoutException extends IOException {
        public AuthorizationTimeoutException(String message) {
            super(message);
        }
    }

    /**
     * Sets AWT headless property required for LocalServerReceiver.
     */
    static {
        LOGGER.info("Setting java.awt.headless=false for Google Auth local server.");
        System.setProperty("java.awt.headless", "false");
    }

    /**
     * Initializes the GoogleAuthManager by setting up the HTTP transport and auth flow.
     *
     * @throws GeneralSecurityException if the transport cannot be initialized.
     * @throws IOException            if the credentials.json file cannot be read or the
     * token directory cannot be accessed.
     */
    public GoogleAuthManager() throws GeneralSecurityException, IOException {
        LOGGER.info("Initializing GoogleAuthManager instance...");
        this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        this.tokensPath = AppDataHandler.getAppDataDirectory().resolve("tokens");

        InputStream in = GoogleAuthManager.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            LOGGER.error("FATAL: credentials.json file not found at resource path: {}", CREDENTIALS_FILE_PATH);
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        this.dataStoreFactory = new FileDataStoreFactory(this.tokensPath.toFile());
        LOGGER.debug("Token data store factory set to path: {}", this.tokensPath);

        this.flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(this.dataStoreFactory)
                .setAccessType("offline")
                .build();
        LOGGER.info("GoogleAuthorizationCodeFlow initialized.");
    }

    /**
     * Initiates a new authorization flow for a user.
     * This will start a local server, open a browser, and wait for the user to grant permission.
     *
     * @param urlDisplayer A consumer that will receive the auth URL to display to the user
     * as a fallback in case the browser doesn't open.
     * @return A valid `Credential` object if successful.
     * @throws IOException if the authorization is denied, timed out, or cancelled.
     */
    public Credential authorizeNewUser(Consumer<String> urlDisplayer) throws IOException {
        LOGGER.info("Initiating new user credential authorization flow.");
        this.timedOut = false;

        // Use setPort(0) to find any available free port. This is much more robust.
        this.receiver = new LocalServerReceiver.Builder().setPort(0).build();
        Timer authTimer = new Timer("AuthTimeoutThread", true); // daemon thread

        try {
            String redirectUri = receiver.getRedirectUri();
            LOGGER.info("Local auth server redirect URI: {}", redirectUri);

            String url = flow.newAuthorizationUrl().setRedirectUri(redirectUri).build();
            LOGGER.info("Authorization URL generated. Displaying to user and attempting to open browser.");
            urlDisplayer.accept(url);

            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                LOGGER.debug("Successfully opened default browser.");
            } catch (Exception e) {
                LOGGER.warn("Automatic browser opening failed. User must manually copy the link.", e);
            }

            LOGGER.info("Waiting for user to complete authorization in browser ({}ms timeout)...", AUTH_TIMEOUT_MS);

            // Start the timeout timer
            authTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    LOGGER.warn("Authorization timed out after {}ms. Aborting.", AUTH_TIMEOUT_MS);
                    timedOut = true;
                    abortAuthorization(); // This forces the receiver.waitForCode() to throw an IOException
                }
            }, AUTH_TIMEOUT_MS);

            String code;
            try {
                // This blocks until a code is received, or stop() is called.
                code = receiver.waitForCode();

                if (code == null) {
                    // This can happen if stop() is called, but not from the timeout.
                    // (e.g., manual abort)
                    authTimer.cancel();
                    if (timedOut) {
                        LOGGER.warn("waitForCode() returned null, and timeout flag is true.");
                        throw new AuthorizationTimeoutException("Authorization timed out.");
                    } else {
                        LOGGER.warn("waitForCode() returned null, but no timeout. Assuming manual cancel.");
                        return null; // Or throw a "Cancelled" exception
                    }
                }
                // Success!
                authTimer.cancel();
                LOGGER.info("Authorization code received.");

            } catch (IOException e) {
                // This block catches:
                // 1. Real "access_denied" from the consent screen.
                // 2. "Socket closed" from our timeout's abortAuthorization().
                // 3. "Socket closed" from a manual UI-driven abortAuthorization().
                authTimer.cancel(); // Stop the timer, the wait is over.

                if (timedOut) {
                    LOGGER.warn("Authorization flow timed out.");
                    throw new IOException("Authorization timed out.", e);
                }

                if (e.getMessage() != null && e.getMessage().contains("access_denied")) {
                    LOGGER.warn("Google reported 'access_denied'. User declined consent.");
                    throw new AccessDeniedException("User access denied by Google (consent-decline).", e);
                }

                // For other IOExceptions (like manual cancel), just re-throw.
                LOGGER.warn("IOException while waiting for code. Assuming timeout or manual cancel.", e);
                throw e; // Re-throws the "Socket closed" or other IO error
            }

            LOGGER.info("Authorization code received, requesting new token.");
            TokenResponse response = flow.newTokenRequest(code).setRedirectUri(redirectUri).execute();
            LOGGER.info("Token response received. Storing credential for 'user'.");
            return flow.createAndStoreCredential(response, "user");

        } finally {
            // Clean up timer and receiver
            authTimer.cancel();
            if (this.receiver != null) {
                LOGGER.debug("Stopping local server receiver.");
                try {
                    this.receiver.stop();
                } catch (IOException e) {
                    LOGGER.error("Error stopping local server receiver", e);
                }
                this.receiver = null;
            }
        }
    }

    /**
     * Loads a stored credential for the "user" and validates it by attempting a token refresh.
     * If validation fails (e.g., token revoked), the stored credential will be deleted.
     *
     * @return A valid, refreshed `Credential` if one exists, or `null` otherwise.
     */
    public Credential loadAndValidateStoredCredential() {
        LOGGER.debug("Attempting to load and validate stored credential for 'user'.");
        try {
            Credential credential = loadStoredCredential();
            if (credential != null) {
                LOGGER.info("Stored credential found. Attempting to refresh token...");
                if (credential.refreshToken()) {
                    LOGGER.info("Token refresh successful. Stored credentials are valid.");
                    return credential;
                } else {
                    LOGGER.warn("Stored credential found, but token refresh failed. Credentials may be expired or revoked.");
                    logout(); // Clean up the bad credentials
                    return null;
                }
            }
            LOGGER.info("No stored credential found for 'user'.");
            return null;
        } catch (IOException e) {
            LOGGER.warn("Could not validate stored credentials, likely expired or revoked. Logging out.", e);
            logout();
            return null;
        }
    }

    /**
     * Loads the credential from the data store without validating it.
     *
     * @return The stored `Credential` or `null` if not found.
     * @throws IOException if there's an error reading the data store.
     */
    private Credential loadStoredCredential() throws IOException {
        return flow.loadCredential("user");
    }

    /**
     * Deletes the stored credential for "user" from the data store.
     */
    public void logout() {
        LOGGER.info("Attempting to log out by deleting stored credentials for 'user'.");
        try {
            // "StoredCredential" is the default DataStore ID used by GoogleAuthorizationCodeFlow
            DataStore<Serializable> credentialDataStore = this.dataStoreFactory.getDataStore("StoredCredential");
            credentialDataStore.delete("user");
            LOGGER.info("Successfully deleted stored credential for 'user'.");
        } catch (IOException e) {
            LOGGER.error("Failed to delete tokens on logout.", e);
        }
    }

    /**
     * Aborts an in-progress authorization flow.
     * This stops the local server, which will cause the blocking
     * `receiver.waitForCode()` call to throw an `IOException`.
     */
    public void abortAuthorization() {
        LOGGER.warn("User aborted authorization flow. Attempting to stop local server receiver.");
        LocalServerReceiver tempReceiver = this.receiver; // Read volatile field once
        if (tempReceiver != null) {
            try {
                tempReceiver.stop();
                LOGGER.info("Authorization receiver stopped successfully by user cancellation.");
            } catch (IOException e) {
                LOGGER.error("Failed to stop authorization receiver during abort.", e);
            }
        }
    }

    /**
     * @return The shared `HttpTransport` instance.
     */
    public HttpTransport getHttpTransport() {
        return this.httpTransport;
    }

    /**
     * @return The shared `JsonFactory` instance.
     */
    public static JsonFactory getJsonFactory() {
        return JSON_FACTORY;
    }
}
