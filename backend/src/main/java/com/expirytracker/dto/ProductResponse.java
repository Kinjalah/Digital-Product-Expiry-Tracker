package com.expirytracker.dto;

public class ProductResponse {
    private Long id;
    private String productName;
    private String extractedText;
    private String expiryDate;
    private long daysRemaining;
    private boolean expiringSoon;

    public ProductResponse(Long id, String productName, String extractedText, String expiryDate, long daysRemaining, boolean expiringSoon) {
        this.id = id;
        this.productName = productName;
        this.extractedText = extractedText;
        this.expiryDate = expiryDate;
        this.daysRemaining = daysRemaining;
        this.expiringSoon = expiringSoon;
    }

    public Long getId() {
        return id;
    }

    public String getProductName() {
        return productName;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public long getDaysRemaining() {
        return daysRemaining;
    }

    public boolean isExpiringSoon() {
        return expiringSoon;
    }
}
