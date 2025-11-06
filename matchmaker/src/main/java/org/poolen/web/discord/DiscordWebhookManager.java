package org.poolen.web.discord;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A utility class for sending messages to a Discord webhook.
 */
public class DiscordWebhookManager {

    private static final Logger logger = LoggerFactory.getLogger(DiscordWebhookManager.class);
    private static final Gson gson = new Gson();

    /**
     * Sends a simple text message to the specified Discord webhook URL.
     *
     * @param webhookUrl The URL of the Discord webhook.
     * @param message    The content of the message to send.
     * @throws IOException If there is an error sending the request.
     */
    public static void sendAnnouncement(String webhookUrl, String message) throws IOException {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            logger.error("Failed to send Discord announcement: Webhook URL is null or empty.");
            throw new IllegalArgumentException("Discord webhook URL cannot be empty, darling!");
        }

        logger.info("Sending Discord announcement to webhook URL (ending with): ...{}",
                webhookUrl.substring(Math.max(0, webhookUrl.length() - 20)));

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(webhookUrl);

            // Discord webhooks expect a JSON payload. The simplest one just has a "content" field.
            Map<String, String> payload = new HashMap<>();
            payload.put("content", message);
            String jsonPayload = gson.toJson(payload);

            StringEntity entity = new StringEntity(jsonPayload, "UTF-8");
            httpPost.setEntity(entity);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");

            // We execute the request and check the response status.
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    logger.info("Successfully sent Discord announcement. Status code: {}", statusCode);
                } else {
                    logger.warn("Failed to send Discord announcement. Received non-OK status code: {}", statusCode);
                    // We'll still throw an IOException to signal a failure to the caller.
                    throw new IOException("Discord webhook request failed with status code: " + statusCode);
                }
            }
        } catch (IOException e) {
            logger.error("An IOException occurred while sending Discord announcement.", e);
            throw e; // Re-throw the exception so the caller (e.g., ExportGroupsStage) can handle it.
        }
    }
}
