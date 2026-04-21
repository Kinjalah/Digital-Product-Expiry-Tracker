package com.expirytracker.service;

import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.expirytracker.entity.Product;
import com.expirytracker.repository.ProductRepository;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class ExpiryReminderService {

    private static final List<DateTimeFormatter> INPUT_DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("uuuu/MM/dd"),
            DateTimeFormatter.ofPattern("uuuu-MM-dd"),
            DateTimeFormatter.ofPattern("MM/uuuu"),
            DateTimeFormatter.ofPattern("MM-uuuu"),
            DateTimeFormatter.ofPattern("MM/yy"),
            DateTimeFormatter.ofPattern("MM-yy")
    );

    private final ProductRepository productRepository;
    private final JavaMailSender mailSender;

    @Value("${app.mail.from:${spring.mail.username:}}")
    private String fromEmail;

    @Value("${app.mail.from-name:${app.brand.name:ExpiryLens}}")
    private String fromName;

    @Value("${app.brand.name:ExpiryLens}")
    private String brandName;

    public ExpiryReminderService(ProductRepository productRepository, JavaMailSender mailSender) {
        this.productRepository = productRepository;
        this.mailSender = mailSender;
    }

    @Transactional
    @Scheduled(cron = "${expiry.reminder.cron:0 0 * * * *}")
    public void sendTwoDayExpiryReminders() {
        sendTwoDayExpiryRemindersNow();
    }

    @Transactional
    public int sendTwoDayExpiryRemindersNow() {
        LocalDate today = LocalDate.now();
        int sentCount = 0;

        for (Product product : productRepository.findAllWithUser()) {
            Optional<LocalDate> parsedExpiry = parseDate(product.getExpiryDate());
            if (parsedExpiry.isEmpty()) {
                continue;
            }

            long daysRemaining = ChronoUnit.DAYS.between(today, parsedExpiry.get());
            if (daysRemaining < 0 || daysRemaining > 2) {
                continue;
            }

            int remainingDays = (int) daysRemaining;

            System.out.println("ExpiryLens reminder match: productId=" + product.getId() + ", productName=" + product.getProductName() + ", userEmail=" + (product.getUser() == null ? "null" : product.getUser().getEmail()) + ", expiryDate=" + product.getExpiryDate() + ", daysRemaining=" + remainingDays);

            if (today.equals(product.getTwoDayReminderSentOn())
                    && Objects.equals(product.getReminderDaysRemainingSent(), remainingDays)) {
                continue;
            }

            if (product.getUser() == null || product.getUser().getEmail() == null || product.getUser().getEmail().isBlank()) {
                continue;
            }

            sendReminderEmail(product.getUser().getEmail(), product.getUser().getName(), product.getProductName(), product.getExpiryDate(), remainingDays);
            product.setTwoDayReminderSentOn(today);
            product.setReminderDaysRemainingSent(remainingDays);
            productRepository.save(product);
            sentCount++;
        }

        return sentCount;
    }

    private void sendReminderEmail(String toEmail, String userName, String productName, String expiryDate, int remainingDays) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            if (fromEmail != null && !fromEmail.isBlank()) {
                helper.setFrom(fromEmail, fromName);
            }

            helper.setTo(toEmail);
            helper.setSubject("ExpiryLens reminder: " + productName + " expires " + getExpiryWindowText(remainingDays));
            helper.setText(
                    "Hello " + (userName == null || userName.isBlank() ? "there" : userName) + ",\n\n"
                        + "This is a friendly reminder from " + brandName + ".\n\n"
                        + "Your product '" + productName + "' will expire " + getExpiryWindowText(remainingDays) + ".\n"
                        + "Expiry date: " + expiryDate + "\n\n"
                        + "Please check it and use or replace it soon to avoid any inconvenience.\n\n"
                        + "Regards,\n"
                        + brandName,
                    false
            );

            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("Failed to send expiry reminder email", e);
        }
    }

    private String getExpiryWindowText(int remainingDays) {
        if (remainingDays <= 0) {
            return "today";
        }
        if (remainingDays == 1) {
            return "in 1 day";
        }
        return "in " + remainingDays + " days";
    }

    private Optional<LocalDate> parseDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank() || "NOT FOUND".equalsIgnoreCase(rawDate.trim())) {
            return Optional.empty();
        }

        String cleaned = rawDate.trim().replace('.', '/').replace('-', '/');

        for (DateTimeFormatter formatter : INPUT_DATE_FORMATS) {
            try {
                return Optional.of(LocalDate.parse(cleaned, formatter));
            } catch (DateTimeParseException ignored) {
                // Try next known format.
            }
        }

        if (cleaned.matches("\\d{4}")) {
            return Optional.of(LocalDate.of(Integer.parseInt(cleaned), 12, 31));
        }

        if (cleaned.matches("\\d{2}/\\d{4}")) {
            String[] p = cleaned.split("/");
            int month = Integer.parseInt(p[0]);
            int year = Integer.parseInt(p[1]);
            if (month >= 1 && month <= 12) {
                return Optional.of(LocalDate.of(year, month, 1));
            }
            return Optional.empty();
        }

        return Optional.empty();
    }
}
