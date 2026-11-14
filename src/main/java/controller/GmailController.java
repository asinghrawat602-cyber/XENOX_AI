package com.xenox.xenox_ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.xenox.xenox_ai.service.GmailService;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/mail")
public class GmailController {

    private final GmailService gmailService;

    public GmailController(GmailService gmailService) {
        this.gmailService = gmailService;
    }

    // STEP 1: Start Google authorization
    @GetMapping("/oauth2/authorize")
    public ResponseEntity<Void> authorize(HttpSession session) {

        String state = UUID.randomUUID().toString();

        session.setAttribute("GMAIL_OAUTH_STATE", state);

        String authorizationUrl =
                gmailService.buildAuthorizationUrl(state);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authorizationUrl))
                .build();
    }

    // STEP 2: Google redirects back here
    @GetMapping("/oauth2/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpSession session
    ) {

        if (error != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Google authorization was not completed: " + error
            );
        }

        String savedState =
                (String) session.getAttribute("GMAIL_OAUTH_STATE");

        session.removeAttribute("GMAIL_OAUTH_STATE");

        if (savedState == null || state == null
                || !savedState.equals(state)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid OAuth state. Please connect Gmail again."
            );
        }

        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Google did not provide an authorization code."
            );
        }

        try {

            JsonNode tokenResponse =
                    gmailService.exchangeCode(code);

            String refreshToken =
                    tokenResponse.path("refresh_token").asText();

            if (refreshToken.isBlank()) {
                throw new IllegalStateException(
                        "Google did not return a refresh token. "
                                + "Reconnect Gmail and approve access."
                );
            }

            // Keep the token on the server, inside this session.
            session.setAttribute(
                    "GMAIL_REFRESH_TOKEN",
                    refreshToken
            );

            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create("/?gmail=connected"))
                    .build();

        } catch (Exception exception) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Gmail authorization failed: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    // STEP 3: Send the email
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendEmail(
            @RequestBody SendEmailRequest request,
            HttpSession session
    ) {

        String refreshToken =
                (String) session.getAttribute("GMAIL_REFRESH_TOKEN");

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Gmail is not connected. Connect Gmail first."
            );
        }

        if (request.sender() == null
                || request.recipient() == null
                || request.subject() == null
                || request.body() == null
                || request.sender().isBlank()
                || request.recipient().isBlank()
                || request.subject().isBlank()
                || request.body().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Sender, recipient, subject and body are required."
            );
        }

        try {

            String accessToken =
                    gmailService.refreshAccessToken(refreshToken);

            String messageId = gmailService.sendEmail(
                    accessToken,
                    request.sender(),
                    request.recipient(),
                    request.subject(),
                    request.body()
            );

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "message", "Email sent successfully!",
                            "messageId", messageId
                    )
            );

        } catch (Exception exception) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Gmail could not send the email: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    public record SendEmailRequest(
            String sender,
            String recipient,
            String subject,
            String body
    ) {}
}
