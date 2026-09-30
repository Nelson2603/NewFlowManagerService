package org.example.newflowmanagerservice.dto_feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;


@JsonIgnoreProperties(ignoreUnknown = true)
public record SubscriptionDto(
        String login,
        String type,
        LocalDateTime expiresAt
) {
}