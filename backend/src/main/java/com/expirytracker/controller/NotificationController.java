package com.expirytracker.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.expirytracker.service.ExpiryReminderService;

@RestController
@RequestMapping("/notifications")
@CrossOrigin(origins = "http://localhost:5173")
public class NotificationController {

    private final ExpiryReminderService expiryReminderService;

    public NotificationController(ExpiryReminderService expiryReminderService) {
        this.expiryReminderService = expiryReminderService;
    }

    @PostMapping("/run-reminders")
    public ResponseEntity<Map<String, Object>> runRemindersNow(
            @RequestParam(value = "userId", required = false) Long userId
    ) {
        int sentCount = expiryReminderService.sendTwoDayExpiryRemindersNow();
        return ResponseEntity.ok(Map.of(
                "message", "Reminder check completed",
                "sentCount", sentCount,
                "userId", userId
        ));
    }
}
