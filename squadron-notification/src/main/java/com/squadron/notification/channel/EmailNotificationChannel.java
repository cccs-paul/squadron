package com.squadron.notification.channel;

import com.squadron.notification.entity.Notification;
import com.squadron.notification.entity.NotificationPreference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;

    @Override
    public String getChannelType() {
        return "EMAIL";
    }

    @Override
    public void send(Notification notification, NotificationPreference preference) {
        String emailAddress = preference != null ? preference.getEmailAddress() : null;
        if (emailAddress == null || emailAddress.isBlank()) {
            log.warn("No email address configured for user {}, marking notification {} as FAILED",
                    notification.getUserId(), notification.getId());
            notification.setStatus("FAILED");
            notification.setErrorMessage("No email address configured");
            return;
        }

        log.info("Sending email notification {} to {}", notification.getId(), emailAddress);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(emailAddress);
            message.setSubject(notification.getSubject());
            message.setText(notification.getBody());
            message.setFrom("noreply@squadron.dev");

            mailSender.send(message);

            notification.setStatus("SENT");
            notification.setSentAt(Instant.now());
            log.info("Email notification {} sent successfully", notification.getId());
        } catch (Exception e) {
            log.error("Failed to send email notification {}: {}", notification.getId(), e.getMessage(), e);
            notification.setStatus("FAILED");
            notification.setErrorMessage("Email delivery failed: " + e.getMessage());
        }
    }
}
