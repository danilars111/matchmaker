package org.poolen.web.github;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * A utility for checking for new releases of the application on GitHub.
 */
public class GitHubUpdateChecker {

    private static final String GITHUB_REPO = "danilars111/matchmaker";
    private static final String CURRENT_VERSION = loadVersionFromPomProperties();

    public static String getCurrentVersion() {
        return CURRENT_VERSION;
    }

    private static String loadVersionFromPomProperties() {
        try (InputStream is = GitHubUpdateChecker.class.getResourceAsStream(
                "/META-INF/maven/org.poolen/matchmaker/pom.properties"
        )) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                return "v" + props.getProperty("version", "dev");
            }
        } catch (IOException e) {
            System.err.println("Could not load version from pom.properties: " + e.getMessage());
        }
        return "dev";
    }

    /**
     * Checks the GitHub API for the latest release and finds the JAR asset.
     *
     * @return An UpdateInfo object with the result.
     * @throws IOException If there's a network issue.
     */
    public static UpdateInfo checkForUpdate() throws IOException {
        String url = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.addHeader("Accept", "application/vnd.github.v3+json");

            String jsonResponse = EntityUtils.toString(httpClient.execute(request).getEntity());
            JsonObject release = JsonParser.parseString(jsonResponse).getAsJsonObject();

            if (release == null || !release.has("tag_name")) {
                return new UpdateInfo(false, "No releases found", null, null);
            }

            String latestVersion = release.get("tag_name").getAsString();
            String releaseUrl = release.get("html_url").getAsString();
            boolean isNewer = isVersionNewer(latestVersion, getCurrentVersion());

            String assetDownloadUrl = null;
            if (release.has("assets") && release.get("assets").isJsonArray()) {
                JsonArray assets = release.getAsJsonArray("assets");
                for (JsonElement assetElement : assets) {
                    JsonObject asset = assetElement.getAsJsonObject();
                    String assetName = asset.get("name").getAsString();
                    if (assetName.endsWith(".jar")) {
                        assetDownloadUrl = asset.get("browser_download_url").getAsString();
                        break;
                    }
                }
            }

            return new UpdateInfo(isNewer, latestVersion, releaseUrl, assetDownloadUrl);
        }
    }

    private static boolean isVersionNewer(String latest, String current) {
        if ("dev".equalsIgnoreCase(current) || current.toUpperCase().contains("SNAPSHOT")) {
            return false; // Don't prompt for updates during development
        }
        return latest.compareToIgnoreCase(current) > 0;
    }

    public record UpdateInfo(boolean isNewVersionAvailable, String latestVersion, String releaseUrl, String assetDownloadUrl) {}
}

