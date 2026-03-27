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
public class SlackNotificationChannel implements NotificationChannel {

    private final WebClient.Builder webClientBuilder;

    @Override
    public String getChannelType() {
        return "SLACK";
    }

    @Override
    public void send(Notification notification, NotificationPreference preference) {
        String webhookUrl = preference != null ? preference.getSlackWebhookUrl() : null;
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("No Slack webhook URL configured for user {}, marking notification {} as FAILED",
                    notification.getUserId(), notification.getId());
            notification.setStatus("FAILED");
            notification.setErrorMessage("No Slack webhook URL configured");
            return;
        }

        log.info("Sending Slack notification {} to webhook for user {}", notification.getId(), notification.getUserId());

        try {
            String text = String.format("*%s*\n%s", notification.getSubject(), notification.getBody());
            Map<String, String> payload = Map.of("text", text);

            webClientBuilder.build()
                    .post()
                    .uri(webhookUrl)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            notification.setStatus("SENT");
            notification.setSentAt(Instant.now());
            log.info("Slack notification {} sent successfully", notification.getId());
        } catch (Exception e) {
            log.error("Failed to send Slack notification {}: {}", notification.getId(), e.getMessage(), e);
            notification.setStatus("FAILED");
            notification.setErrorMessage("Slack delivery failed: " + e.getMessage());
        }
    }
}
