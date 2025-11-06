package org.poolen.web.github;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * A utility for checking for new releases of the application on GitHub.
 */
public class GitHubUpdateChecker {

    private static final Logger logger = LoggerFactory.getLogger(GitHubUpdateChecker.class);

    private static final String GITHUB_REPO = "danilars111/matchmaker";
    private static final String CURRENT_VERSION = loadVersionFromPomProperties();

    public static String getCurrentVersion() {
        return CURRENT_VERSION;
    }

    private static String loadVersionFromPomProperties() {
        logger.info("Loading version from pom.properties...");
        try (InputStream is = GitHubUpdateChecker.class.getResourceAsStream(
                "/META-INF/maven/org.poolen/matchmaker/pom.properties"
        )) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String version = props.getProperty("version", "dev");
                logger.info("Successfully loaded application version: {}", version);
                return "v" + version;
            }
        } catch (IOException e) {
            logger.error("Could not load version from pom.properties: {}", e.getMessage(), e);
        }
        logger.warn("Could not find pom.properties. Defaulting to 'dev' version.");
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
        logger.info("Checking for update at GitHub API: {}", url);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.addHeader("Accept", "application/vnd.github.v3+json");

            String jsonResponse = EntityUtils.toString(httpClient.execute(request).getEntity());
            JsonObject release = JsonParser.parseString(jsonResponse).getAsJsonObject();

            if (release == null || !release.has("tag_name")) {
                logger.warn("GitHub release JSON response is null or does not have 'tag_name'. No update info available.");
                return new UpdateInfo(false, "No releases found", null, null);
            }

            String latestVersion = release.get("tag_name").getAsString();
            String releaseUrl = release.get("html_url").getAsString();
            boolean isNewer = isVersionNewer(latestVersion, getCurrentVersion());
            logger.info("Latest version tag: {}. Current version: {}. Is newer: {}", latestVersion, getCurrentVersion(), isNewer);

            String assetDownloadUrl = null;
            if (release.has("assets") && release.get("assets").isJsonArray()) {
                JsonArray assets = release.getAsJsonArray("assets");
                for (JsonElement assetElement : assets) {
                    JsonObject asset = assetElement.getAsJsonObject();
                    String assetName = asset.get("name").getAsString();
                    if (assetName.endsWith(".jar")) {
                        assetDownloadUrl = asset.get("browser_download_url").getAsString();
                        logger.debug("Found .jar asset '{}' with download URL: {}", assetName, assetDownloadUrl);
                        break;
                    }
                }
                if (assetDownloadUrl == null) {
                    logger.warn("No .jar asset found in the latest release assets for version {}.", latestVersion);
                }
            } else {
                logger.warn("Latest release {} contains no 'assets' array.", latestVersion);
            }

            return new UpdateInfo(isNewer, latestVersion, releaseUrl, assetDownloadUrl);
        } catch (IOException e) {
            logger.error("IOException while checking for GitHub update.", e);
            throw e;
        }
    }

    private static boolean isVersionNewer(String latest, String current) {
        if ("dev".equalsIgnoreCase(current) || current.toUpperCase().contains("SNAPSHOT")) {
            logger.debug("Current version is 'dev' or 'SNAPSHOT'. Skipping update check.");
            return false; // Don't prompt for updates during development
        }
        int comparison = latest.compareToIgnoreCase(current);
        logger.debug("Comparing versions: Latest '{}', Current '{}'. Comparison result: {}", latest, current, comparison);
        return comparison > 0;
    }

    public record UpdateInfo(boolean isNewVersionAvailable, String latestVersion, String releaseUrl, String assetDownloadUrl) {}
}
