package com.squadron.notification.channel;

import com.squadron.notification.entity.Notification;
import com.squadron.notification.entity.NotificationPreference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TeamsNotificationChannel implements NotificationChannel {

    private final WebClient.Builder webClientBuilder;

    @Override
    public String getChannelType() {
        return "TEAMS";
    }

    @Override
    public void send(Notification notification, NotificationPreference preference) {
        String webhookUrl = preference != null ? preference.getTeamsWebhookUrl() : null;
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("No Teams webhook URL configured for user {}, marking notification {} as FAILED",
                    notification.getUserId(), notification.getId());
            notification.setStatus("FAILED");
            notification.setErrorMessage("No Teams webhook URL configured");
            return;
        }

        log.info("Sending Teams notification {} to webhook for user {}", notification.getId(), notification.getUserId());

        try {
            Map<String, Object> payload = Map.of(
                    "@type", "MessageCard",
                    "@context", "http://schema.org/extensions",
                    "summary", notification.getSubject(),
                    "themeColor", "0076D7",
                    "title", notification.getSubject(),
                    "text", notification.getBody()
            );

            webClientBuilder.build()
                    .post()
                    .uri(webhookUrl)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            notification.setStatus("SENT");
            notification.setSentAt(Instant.now());
            log.info("Teams notification {} sent successfully", notification.getId());
        } catch (Exception e) {
            log.error("Failed to send Teams notification {}: {}", notification.getId(), e.getMessage(), e);
            notification.setStatus("FAILED");
            notification.setErrorMessage("Teams delivery failed: " + e.getMessage());
        }
    }
}
