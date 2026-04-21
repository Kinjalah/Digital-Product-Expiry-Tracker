package com.expirytracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

@Service
public class GoogleVisionService {

    @Value("${google.vision.api.key:}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String extractTextFromImage(File file) {
        try {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("Google Vision API key is missing. Set google.vision.api.key.");
            }

            byte[] imageBytes = Files.readAllBytes(file.toPath());
            String base64 = Base64.getEncoder().encodeToString(imageBytes);

            String requestBody = "{\n" +
                    "  \"requests\": [\n" +
                    "    {\n" +
                    "      \"image\": {\"content\": \"" + base64 + "\"},\n" +
                    "      \"features\": [{\"type\": \"TEXT_DETECTION\"}]\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";

            URL url = new URL("https://vision.googleapis.com/v1/images:annotate?key=" + apiKey);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            InputStream responseStream = status >= 200 && status < 300
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (responseStream == null) {
                throw new IllegalStateException("Google Vision API returned status " + status + " with empty response.");
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Google Vision API error " + status + ": " + response);
            }

            return extractTextFromJson(response.toString());

        } catch (IOException e) {
            throw new IllegalStateException("Failed to call Google Vision API", e);
        }
    }

    // ✅ Extract full text from JSON response
    private String extractTextFromJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode responses = root.path("responses");

            if (!responses.isArray() || responses.isEmpty()) {
                return "";
            }

            JsonNode first = responses.get(0);

            String fullText = first.path("fullTextAnnotation").path("text").asText("");
            if (!fullText.isBlank()) {
                return fullText.replace("\n", " ").trim();
            }

            JsonNode textAnnotations = first.path("textAnnotations");
            if (textAnnotations.isArray() && !textAnnotations.isEmpty()) {
                String fallback = textAnnotations.get(0).path("description").asText("");
                return fallback.replace("\n", " ").trim();
            }

            return "";
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse Google Vision response", e);
        }
    }
}