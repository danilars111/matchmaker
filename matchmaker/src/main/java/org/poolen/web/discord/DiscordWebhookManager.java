package org.poolen.web.discord;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A utility class for sending messages to a Discord webhook.
 */
public class DiscordWebhookManager {

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
            throw new IllegalArgumentException("Discord webhook URL cannot be empty, darling!");
        }

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

            // We execute the request but don't need to do anything with the response for a simple webhook.
            httpClient.execute(httpPost);
        }
    }
}
