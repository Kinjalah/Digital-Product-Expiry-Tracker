package com.expirytracker.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String productName;

    @Column(length = 5000)
    private String extractedText;

    @Column(nullable = false)
    private String expiryDate;

    private LocalDate twoDayReminderSentOn;
    private Integer reminderDaysRemainingSent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    // Getter & Setter

    public Long getId() {
        return id;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    public LocalDate getTwoDayReminderSentOn() {
        return twoDayReminderSentOn;
    }

    public void setTwoDayReminderSentOn(LocalDate twoDayReminderSentOn) {
        this.twoDayReminderSentOn = twoDayReminderSentOn;
    }

    public Integer getReminderDaysRemainingSent() {
        return reminderDaysRemainingSent;
    }

    public void setReminderDaysRemainingSent(Integer reminderDaysRemainingSent) {
        this.reminderDaysRemainingSent = reminderDaysRemainingSent;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}