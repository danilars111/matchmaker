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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

/**
 * Handles the OAuth 2.0 authorization flow with Google.
 * It ensures the user grants necessary permissions and securely stores
 * credentials for future sessions.
 */
public class GoogleAuthManager {

    private static final String CREDENTIALS_FILE_PATH = "/credentials/credentials.json";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    // Directory to store the user's access and refresh tokens.
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static HttpTransport HTTP_TRANSPORT;

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }


    /**
     * Creates an authorized Credential object.
     * If the user has not previously granted access, it will launch a browser
     * to obtain the user's consent.
     *
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found or read.
     */
    public static Credential getCredentials() throws IOException {
        InputStream in = GoogleAuthManager.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new IOException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        // This is the line that will either load existing tokens or pop open a browser for the user to log in.
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    /**
     * Deletes the stored user credentials, effectively logging them out.
     * The user will be prompted to log in again on the next connection attempt.
     * @throws IOException if the token file cannot be deleted.
     */
    public static void logout() throws IOException {
        File tokenFolder = new File(TOKENS_DIRECTORY_PATH);
        if (tokenFolder.exists()) {
            File credentialFile = new File(tokenFolder, "StoredCredential");
            if (credentialFile.exists()) {
                if (!credentialFile.delete()) {
                    // Attempt to delete the directory if the file deletion fails or if it's the only thing left.
                    // This is a more robust way to clean up.
                    String[] files = tokenFolder.list();
                    if (files == null || files.length == 0) {
                        if (!tokenFolder.delete()) {
                            throw new IOException("Failed to delete token folder.");
                        }
                    } else {
                        throw new IOException("Failed to delete token file.");
                    }
                }
            }
        }
    }


    /**
     * Gets the shared HttpTransport instance.
     * @return The HttpTransport.
     */
    public static HttpTransport getHttpTransport() {
        return HTTP_TRANSPORT;
    }

    /**
     * Gets the shared JsonFactory instance.
     * @return The JsonFactory.
     */
    public static JsonFactory getJsonFactory() {
        return JSON_FACTORY;
    }
}

