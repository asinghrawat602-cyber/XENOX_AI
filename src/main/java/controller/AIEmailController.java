package com.xenox.xenox_ai.controller;

import com.xenox.xenox_ai.dto.GenerateEmailRequest;
import com.xenox.xenox_ai.dto.GenerateEmailResponse;
import com.xenox.xenox_ai.service.GeminiEmailService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AIEmailController {

    private final GeminiEmailService geminiEmailService;

    public AIEmailController(
            GeminiEmailService geminiEmailService
    ) {
        this.geminiEmailService = geminiEmailService;
    }

    @PostMapping("/generate-email")
    public ResponseEntity<GenerateEmailResponse> generateEmail(
            @Valid @RequestBody GenerateEmailRequest request
    ) {

        try {

            String body = geminiEmailService.generateEmail(
                    request.sender(),
                    request.recipient(),
                    request.subject(),
                    request.information()
            );

            return ResponseEntity.ok(
                    new GenerateEmailResponse(body)
            );

        } catch (Exception exception) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "XENOX could not generate the email. Check the Gemini configuration.",
                    exception
            );
        }
    }
}
