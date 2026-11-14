package com.xenox.xenox_ai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record GenerateEmailRequest(

        @NotBlank
        @Email
        String sender,

        @NotBlank
        @Email
        String recipient,

        @NotBlank
        String subject,

        @NotBlank
        String information

) {}
