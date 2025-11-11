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
import org.poolen.web.events.AuthStatusChangedEvent; // <-- Our new event!
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired; // <-- New!
import org.springframework.context.ApplicationEventPublisher; // <-- The "Spring BS"!
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
 * ... (rest of your lovely javadoc)
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
    private final ApplicationEventPublisher eventPublisher; // <-- Look! The publisher!

    /**
     * This holds the state for an *in-progress* authorization attempt.
     * It is volatile to ensure visibility between the auth thread and the timeout/abort thread.
     */
    private volatile LocalServerReceiver receiver;
    // ... (rest of your fields) ...
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
        // ... (static block is perfect) ...
        LOGGER.info("Setting java.awt.headless=false for Google Auth local server.");
        System.setProperty("java.awt.headless", "false");
    }

    /**
     * Initializes the GoogleAuthManager by setting up the HTTP transport and auth flow.
     *
     * @param eventPublisher Spring's event publisher, injected automatically.
     * @throws GeneralSecurityException if the transport cannot be initialized.
     * @throws IOException            if the credentials.json file cannot be read or the
     * token directory cannot be accessed.
     */
    @Autowired // This tells Spring to inject the publisher for us!
    public GoogleAuthManager(ApplicationEventPublisher eventPublisher) throws GeneralSecurityException, IOException {
        LOGGER.info("Initializing GoogleAuthManager instance...");
        this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        this.tokensPath = AppDataHandler.getAppDataDirectory().resolve("tokens");
        this.eventPublisher = eventPublisher; // <-- We save it!

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
     * ...
     * @return A valid `Credential` object if successful.
     * @throws IOException if the authorization is denied, timed out, or cancelled.
     */
    public Credential authorizeNewUser(Consumer<String> urlDisplayer) throws IOException {
        LOGGER.info("Initiating new user credential authorization flow.");
        this.timedOut = false;

        // ... (rest of your method is perfect) ...
        this.receiver = new LocalServerReceiver.Builder().setPort(0).build();
        Timer authTimer = new Timer("AuthTimeoutThread", true); // daemon thread

        try {
            // ... (all the try logic for getting the code) ...
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
                // ... (all the waitForCode logic) ...
                code = receiver.waitForCode();

                if (code == null) {
                    // ... (all the error handling) ...
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
                // ... (all the exception handling) ...
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

            Credential credential = flow.createAndStoreCredential(response, "user");

            // --- LOOK, MY LOVE! WE PUBLISH THE EVENT! ---
            // This is perfect, it only happens on a *new* login!
            LOGGER.info("Publishing AuthStatusChangedEvent after new user auth.");
            this.eventPublisher.publishEvent(new AuthStatusChangedEvent(this));
            // --- All done! ---

            return credential;

        } finally {
            // ... (finally block is perfect) ...
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
     * This is a "quiet" check and does NOT publish an event.
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

                    // --- EVENT PUBLISH REMOVED! ---
                    // This is now a quiet, well-behaved little check,
                    // just as you wanted, my clever girl! No more loop!
                    // ---

                    return credential;
                } else {
                    LOGGER.warn("Stored credential found, but token refresh failed. Credentials may be expired or revoked.");
                    logout(); // This will publish its *own* event!
                    return null;
                }
            }
            LOGGER.info("No stored credential found for 'user'.");
            return null;
        } catch (IOException e) {
            LOGGER.warn("Could not validate stored credentials, likely expired or revoked. Logging out.", e);
            logout(); // This will publish its *own* event!
            return null;
        }
    }

    /**
     * Loads the credential from the data store without validating it.
     * ...
     */
    private Credential loadStoredCredential() throws IOException {
        return flow.loadCredential("user");
    }

    /**
     * Deletes the stored credential for "user" from the data store.
     * This action *will* publish an AuthStatusChangedEvent.
     */
    public void logout() {
        LOGGER.info("Attempting to log out by deleting stored credentials for 'user'.");
        try {
            // "StoredCredential" is the default DataStore ID used by GoogleAuthorizationCodeFlow
            DataStore<Serializable> credentialDataStore = this.dataStoreFactory.getDataStore("StoredCredential");
            credentialDataStore.delete("user");
            LOGGER.info("Successfully deleted stored credential for 'user'.");

            // --- AND WE PUBLISH ON LOGOUT! ---
            // This is also perfect!
            LOGGER.info("Publishing AuthStatusChangedEvent after logout.");
            this.eventPublisher.publishEvent(new AuthStatusChangedEvent(this));
            // --- All clean! ---

        } catch (IOException e) {
            LOGGER.error("Failed to delete tokens on logout.", e);
        }
    }

    /**
     * Aborts an in-progress authorization flow.
     * ...
     */
    public void abortAuthorization() {
        // ... (this method is perfect, no changes needed) ...
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
