package com.xenox.xenox_ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class GmailService {

    private final HttpClient httpClient =
            HttpClient.newHttpClient();

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @Value("${gmail.client-id}")
    private String clientId;

    @Value("${gmail.client-secret}")
    private String clientSecret;

    @Value("${gmail.redirect-uri}")
    private String redirectUri;

    private static final String TOKEN_URL =
            "https://oauth2.googleapis.com/token";

    private static final String GMAIL_API =
            "https://gmail.googleapis.com/gmail/v1/users/me";

    // ==========================================
    // GOOGLE AUTHORIZATION URL
    // ==========================================

    public String buildAuthorizationUrl(String state) {

        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode(
                "https://www.googleapis.com/auth/gmail.send"
        )
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + encode(state);
    }

    // ==========================================
    // EXCHANGE AUTHORIZATION CODE
    // ==========================================

    public JsonNode exchangeCode(String code)
            throws Exception {

        String form =
                "code=" + encode(code)
                        + "&client_id=" + encode(clientId)
                        + "&client_secret=" + encode(clientSecret)
                        + "&redirect_uri=" + encode(redirectUri)
                        + "&grant_type=authorization_code";

        return postForm(TOKEN_URL, form);
    }

    // ==========================================
    // REFRESH ACCESS TOKEN
    // ==========================================

    public String refreshAccessToken(String refreshToken)
            throws Exception {

        String form =
                "client_id=" + encode(clientId)
                        + "&client_secret=" + encode(clientSecret)
                        + "&refresh_token=" + encode(refreshToken)
                        + "&grant_type=refresh_token";

        JsonNode response = postForm(TOKEN_URL, form);

        String accessToken =
                response.path("access_token").asText();

        if (accessToken.isBlank()) {
            throw new IllegalStateException(
                    "Google did not return an access token."
            );
        }

        return accessToken;
    }

    // ==========================================
    // SEND EMAIL THROUGH GMAIL API
    // ==========================================

    public String sendEmail(
            String accessToken,
            String sender,
            String recipient,
            String subject,
            String body
    ) throws Exception {

        if (accessToken == null || accessToken.isBlank()
                || sender == null || recipient == null
                || subject == null || body == null
                || sender.isBlank() || recipient.isBlank()
                || subject.isBlank() || body.isBlank()
                || sender.contains("\r") || sender.contains("\n")
                || recipient.contains("\r") || recipient.contains("\n")) {

            throw new IllegalArgumentException(
                    "Invalid or missing email information."
            );
        }

        // Encode email subject
        String encodedSubject =
                Base64.getEncoder().encodeToString(
                        subject.getBytes(StandardCharsets.UTF_8)
                );

        // Encode email body
        String encodedBody =
                Base64.getEncoder().encodeToString(
                        body.getBytes(StandardCharsets.UTF_8)
                );

        // Build MIME email
        String mimeMessage =
                "To: " + recipient + "\r\n"
                        + "From: " + sender + "\r\n"
                        + "Subject: =?UTF-8?B?"
                        + encodedSubject + "?=\r\n"
                        + "MIME-Version: 1.0\r\n"
                        + "Content-Type: text/plain; charset=UTF-8\r\n"
                        + "Content-Transfer-Encoding: base64\r\n"
                        + "\r\n"
                        + encodedBody;

        // Gmail requires URL-safe Base64
        String rawMessage =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                mimeMessage.getBytes(
                                        StandardCharsets.UTF_8
                                )
                        );

        String json =
                objectMapper.writeValueAsString(
                        Map.of("raw", rawMessage)
                );

        // Send request to Gmail API
        HttpRequest sendRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(
                                GMAIL_API + "/messages/send"
                        ))
                        .header(
                                "Authorization",
                                "Bearer " + accessToken
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofString(json)
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        sendRequest,
                        HttpResponse.BodyHandlers.ofString()
                );

        checkResponse(response);

        String messageId =
                objectMapper.readTree(response.body())
                        .path("id")
                        .asText();

        if (messageId.isBlank()) {
            throw new IllegalStateException(
                    "Gmail did not return a message ID."
            );
        }

        return messageId;
    }

    // ==========================================
    // HTTP FORM REQUEST
    // ==========================================

    private JsonNode postForm(
            String url,
            String form
    ) throws Exception {

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header(
                                "Content-Type",
                                "application/x-www-form-urlencoded"
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofString(form)
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        checkResponse(response);

        return objectMapper.readTree(response.body());
    }

    // ==========================================
    // GOOGLE API RESPONSE VALIDATION
    // ==========================================

    private void checkResponse(
            HttpResponse<String> response
    ) throws Exception {

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            throw new IllegalStateException(
                    "Google API error (HTTP "
                            + response.statusCode()
                            + "): "
                            + response.body()
            );
        }
    }

    // ==========================================
    // URL ENCODING
    // ==========================================

    private String encode(String value) {

        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }
}
