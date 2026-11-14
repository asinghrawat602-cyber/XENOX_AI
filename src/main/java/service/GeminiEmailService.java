
package com.xenox.xenox_ai.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeminiEmailService {

    private static final Logger log =
            LoggerFactory.getLogger(GeminiEmailService.class);

    private final Client client;

    @Value("${gemini.model:gemini-3.8-flash}")
    private String model;

    public GeminiEmailService() {

        String apiKey = System.getenv("GOOGLE_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_API_KEY environment variable is missing."
            );
        }

        this.client = Client.builder()
                .apiKey(apiKey)
                .httpOptions(
                        HttpOptions.builder()
                                .timeout(60000)
                                .retryOptions(
                                        HttpRetryOptions.builder()
                                                .attempts(1)
                                )
                                .build()
                )
                .build();

        log.info("XENOX Gemini client initialized.");
    }

    public String generateEmail(
            String sender,
            String recipient,
            String subject,
            String information
    ) {

        String prompt = """
                You are XENOX, a premium AI email writing assistant.

                Write a professional, natural and well-structured email.

                SENDER: %s
                RECIPIENT: %s
                SUBJECT: %s

                USER'S INSTRUCTIONS:
                %s

                REQUIREMENTS:
                1. Focus carefully on the user's instructions.
                2. Use a professional and natural tone.
                3. Include an appropriate greeting.
                4. Organize the message into readable paragraphs.
                5. Include a suitable closing.
                6. Do not invent personal facts.
                7. Return ONLY the email body.
                8. Do not include explanations or markdown code fences.
                """.formatted(sender, recipient, subject, information);

        log.info("XENOX is requesting email generation from Gemini.");

        try {

            GenerateContentResponse response =
                    client.models.generateContent(
                            model,
                            prompt,
                            null
                    );

            String result = response.text();

            if (result == null || result.isBlank()) {
                throw new IllegalStateException(
                        "Gemini returned an empty response."
                );
            }

            log.info("Gemini email generation successful.");

            return result.trim();

        } catch (Exception exception) {

            log.error(
                    "Gemini email generation failed: {}",
                    exception.getMessage(),
                    exception
            );

            throw new IllegalStateException(
                    "Gemini generation failed: " + exception.getMessage(),
                    exception
            );
        }
    }
}
