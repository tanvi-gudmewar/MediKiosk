package com.medikiosk.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public String generateContent(String prompt) throws Exception {

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=" + apiKey;

        // Build the JSON request body Gemini expects
        String requestBody = mapper.writeValueAsString(
            new Object() {
                public final Object[] contents = {
                    new Object() {
                        public final Object[] parts = {
                            new Object() {
                                public final String text = prompt;
                            }
                        };
                    }
                };
            }
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Extract just the text part from Gemini's response structure
        JsonNode root = mapper.readTree(response.body());
        String text = root
            .path("candidates").get(0)
            .path("content")
            .path("parts").get(0)
            .path("text").asText();

        return text;
    }

    public String generateContentWithFile(String prompt, byte[] fileBytes, String mimeType) throws Exception {

    String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=" + apiKey;

    String base64Data = java.util.Base64.getEncoder().encodeToString(fileBytes);

    String requestBody = mapper.writeValueAsString(
        new Object() {
            public final Object[] contents = {
                new Object() {
                    public final Object[] parts = {
                        new Object() {
                            public final String text = prompt;
                        },
                        new Object() {
                            public final Object inline_data = new Object() {
                                public final String mime_type = mimeType;
                                public final String data = base64Data;
                            };
                        }
                    };
                }
            };
        }
    );

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    JsonNode root = mapper.readTree(response.body());
    String text = root
        .path("candidates").get(0)
        .path("content")
        .path("parts").get(0)
        .path("text").asText();

    return text;
}
}
