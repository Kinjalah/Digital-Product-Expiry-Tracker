package com.expirytracker.service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.expirytracker.dto.ProductRequest;
import com.expirytracker.dto.ProductResponse;
import com.expirytracker.entity.Product;
import com.expirytracker.entity.User;
import com.expirytracker.ocr.OCRService;
import com.expirytracker.repository.ProductRepository;
import com.expirytracker.repository.UserRepository;

@Service
public class ProductService {

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

    private final ProductRepository repository;
    private final UserRepository userRepository;
    private final GoogleVisionService googleVisionService;
    private final OCRService ocrService;

    public ProductService(ProductRepository repository, UserRepository userRepository, GoogleVisionService googleVisionService, OCRService ocrService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.googleVisionService = googleVisionService;
        this.ocrService = ocrService;
    }

    public ProductResponse processProduct(Long userId, String productName, MultipartFile file) {

        User user = getUser(userId);
        File tempFile = null;

        try {
            String normalizedProductName = normalizeOcrProductName(productName);

            if (file == null || file.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "Product image is required");
            }

            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.jpg";
            String extension = getExtension(originalName);
            tempFile = File.createTempFile("expiry-upload-", extension);
            file.transferTo(tempFile);

            String text = extractTextWithFallback(tempFile);
            String expiryDate = extractExpiryDate(text);

            Product product = new Product();
            product.setProductName(normalizedProductName);
            product.setExtractedText(text);
            product.setExpiryDate(expiryDate);
            product.setTwoDayReminderSentOn(null);
            product.setReminderDaysRemainingSent(null);
            product.setUser(user);

            return toResponse(repository.save(product));

        } catch (IOException e) {
            throw new IllegalStateException("Failed to process product image for OCR", e);
        } finally {
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                tempFile.deleteOnExit();
            }
        }
    }

    private String normalizeOcrProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Product name is required");
        }
        return productName.trim();
    }

    public ProductResponse addProduct(Long userId, ProductRequest request) {
        User user = getUser(userId);

        if (request.getProductName() == null || request.getProductName().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Product name is required");
        }

        if (request.getExpiryDate() == null || request.getExpiryDate().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Expiry date is required");
        }

        Product product = new Product();
        product.setProductName(request.getProductName().trim());
        product.setExpiryDate(normalizeManualExpiryDate(request.getExpiryDate()));
        product.setExtractedText("Manual entry");
        product.setTwoDayReminderSentOn(null);
        product.setReminderDaysRemainingSent(null);
        product.setUser(user);

        return toResponse(repository.save(product));
    }

    public List<ProductResponse> getProducts(Long userId) {
        return repository.findByUserIdOrderByIdDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ProductResponse> getExpiringProducts(Long userId, int days) {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(days);

        List<ProductResponse> expiring = new ArrayList<>();

        for (Product product : repository.findByUserIdOrderByIdDesc(userId)) {
            Optional<LocalDate> parsed = parseDate(product.getExpiryDate());
            if (parsed.isEmpty()) {
                continue;
            }

            LocalDate date = parsed.get();
            if (!date.isBefore(today) && !date.isAfter(threshold)) {
                expiring.add(toResponse(product));
            }
        }

        expiring.sort(Comparator.comparingLong(ProductResponse::getDaysRemaining));
        return expiring;
    }

    public ProductResponse updateProduct(Long userId, Long productId, ProductRequest request) {
        Product product = repository.findByIdAndUserId(productId, userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found"));

        if (request.getProductName() != null && !request.getProductName().isBlank()) {
            product.setProductName(request.getProductName().trim());
        }

        if (request.getExpiryDate() != null && !request.getExpiryDate().isBlank()) {
            product.setExpiryDate(normalizeManualExpiryDate(request.getExpiryDate()));
            product.setTwoDayReminderSentOn(null);
            product.setReminderDaysRemainingSent(null);
        }

        return toResponse(repository.save(product));
    }

    public void deleteProduct(Long userId, Long productId) {
        Product product = repository.findByIdAndUserId(productId, userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found"));
        repository.delete(product);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
    }

    private String extractTextWithFallback(File tempFile) {
        try {
            String visionText = googleVisionService.extractTextFromImage(tempFile);
            if (visionText != null && !visionText.isBlank()) {
                return visionText;
            }
        } catch (Exception e) {
            System.out.println("Google Vision failed: " + e.getMessage());
        }

        String localOcrText = ocrService.extractText(tempFile);
        return localOcrText == null ? "" : localOcrText;
    }

    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return ".tmp";
        }
        return filename.substring(lastDot);
    }

    private String cleanText(String text) {
        return text
                .replaceAll("[^a-zA-Z0-9:/\\-.\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeManualExpiryDate(String rawInput) {
        String normalized = extractExpiryDate(rawInput);
        if ("Not Found".equalsIgnoreCase(normalized)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Unsupported expiry date format. Supported: DD/MM/YYYY, YYYY/MM, MM/YYYY, text month (e.g., Feb 2025), YYYY, and keyword-based formats like EXP, BEST BEFORE, USE BY"
            );
        }
        return normalized;
    }

    private String extractExpiryDate(String text) {

        if (text == null || text.isEmpty()) return "Not Found";

        text = cleanText(text).toUpperCase(Locale.ROOT);

        text = text.replace("8EFORE", "BEFORE");
        text = text.replace("EXPIRES", "EXP");
        text = text.replace("EXPIRY", "EXP");
        text = text.replaceAll("(?<=\\d)\\s*[-./:]\\s*(?=\\d)", "/");
        text = text.replaceAll("/+", "/");
        text = text.replaceAll("[|,]", "/");
        text = text.replaceAll("(?<=\\b)[OI](?=\\d)", "0");
        text = text.replaceAll("(?<=\\d)[OI](?=\\d)", "0");
        text = text.replaceAll("(?<=\\d)S(?=\\d)", "5");

        text = convertMonthToNumber(text);

        String date = extractUsingKeywords(text);
        if (date != null) return date;

        date = extractUsingPatterns(text);
        if (date != null) return date;

        return "Not Found";
    }

    private String convertMonthToNumber(String text) {
        return text.replaceAll("\\bJAN(?:UARY)?\\b", "01")
                .replaceAll("\\bFEB(?:RUARY)?\\b", "02")
                .replaceAll("\\bMAR(?:CH)?\\b", "03")
                .replaceAll("\\bAPR(?:IL)?\\b", "04")
                .replaceAll("\\bMAY\\b", "05")
                .replaceAll("\\bJUN(?:E)?\\b", "06")
                .replaceAll("\\bJUL(?:Y)?\\b", "07")
                .replaceAll("\\bAUG(?:UST)?\\b", "08")
                .replaceAll("\\bSEP(?:TEMBER)?\\b", "09")
                .replaceAll("\\bOCT(?:OBER)?\\b", "10")
                .replaceAll("\\bNOV(?:EMBER)?\\b", "11")
                .replaceAll("\\bDEC(?:EMBER)?\\b", "12");
    }

    private String extractUsingKeywords(String text) {

        try {
            Pattern pattern = Pattern.compile(
                    "(EXP|BEST\\s*BEFORE|USE\\s*BY|BBE|BB)[^0-9]{0,40}" +
                        "((?<!\\d)\\d{1,2}[-/. ]\\d{1,2}[-/. ]\\d{2,4}(?!\\d)" +
                        "|(?<!\\d)\\d{4}[-/. ]\\d{1,2}[-/. ]\\d{1,2}(?!\\d)" +
                        "|(?<!\\d)\\d{4}[-/. ]\\d{1,2}(?!\\d)" +
                        "|(?<!\\d)\\d{1,2}[-/. ]\\d{2,4}(?!\\d)" +
                        "|(?<!\\d)\\d{4}(?!\\d))"
            );

            Matcher matcher = pattern.matcher(text);

            if (matcher.find()) {
                return normalizeDateCandidate(matcher.group(2));
            }

        } catch (Exception e) {
            System.out.println("Regex error: " + e.getMessage());
        }

        return null;
    }

    private String extractUsingPatterns(String text) {

        String[] patterns = {
            "(?<!\\d)\\d{1,2}[-/. ]\\d{1,2}[-/. ]\\d{2,4}(?!\\d)",
            "(?<!\\d)\\d{4}[-/. ]\\d{1,2}[-/. ]\\d{1,2}(?!\\d)",
            "(?<!\\d)\\d{4}[-/. ]\\d{1,2}(?!\\d)",
            "(?<!\\d)\\d{1,2}[-/. ]\\d{2,4}(?!\\d)",
            "(?<!\\d)\\d{4}(?!\\d)"
        };

        List<String> candidates = new ArrayList<>();

        for (String p : patterns) {
            try {
                Pattern pattern = Pattern.compile(p);
                Matcher matcher = pattern.matcher(text);

                while (matcher.find()) {
                    candidates.add(normalizeDateCandidate(matcher.group()));
                }

            } catch (Exception e) {
                System.out.println("Pattern error: " + e.getMessage());
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        String bestRaw = candidates.get(0);
        int bestSpecificity = getSpecificityScore(bestRaw);
        LocalDate bestDate = parseDate(bestRaw).orElse(null);

        for (String candidate : candidates) {
            int specificity = getSpecificityScore(candidate);
            LocalDate parsed = parseDate(candidate).orElse(null);

            if (specificity > bestSpecificity) {
                bestSpecificity = specificity;
                bestDate = parsed;
                bestRaw = candidate;
                continue;
            }

            if (specificity < bestSpecificity || parsed == null) {
                continue;
            }

            if (bestDate == null || parsed.isAfter(bestDate)) {
                bestDate = parsed;
                bestRaw = candidate;
            }
        }

        return bestRaw;
    }

    private int getSpecificityScore(String normalizedDate) {
        String[] parts = normalizedDate.split("/");
        if (parts.length == 3) {
            return 3;
        }
        if (parts.length == 2) {
            return 2;
        }
        return 1;
    }

    private String normalizeDateCandidate(String raw) {
        if (raw == null) {
            return null;
        }

        String normalized = raw.trim().replaceAll("[.\\s-]+", "/");
        String[] parts = normalized.split("/");

        if (parts.length == 3) {
            if (parts[0].length() == 4) {
                return parts[0] + "/" + padLeft2(parts[1]) + "/" + padLeft2(parts[2]);
            }
            String p1 = padLeft2(parts[0]);
            String p2 = padLeft2(parts[1]);
            String p3 = normalizeYear(parts[2]);
            return p1 + "/" + p2 + "/" + p3;
        }

        if (parts.length == 2) {
            if (parts[0].length() == 4) {
                return parts[0] + "/" + padLeft2(parts[1]);
            }
            return padLeft2(parts[0]) + "/" + normalizeYear(parts[1]);
        }

        if (normalized.matches("\\d{4}")) {
            return normalized;
        }

        return raw;
    }

    private String padLeft2(String value) {
        String v = value.replaceAll("\\D", "");
        if (v.length() == 1) {
            return "0" + v;
        }
        return v;
    }

    private String normalizeYear(String year) {
        String y = year.replaceAll("\\D", "");
        if (y.length() == 2) {
            int yy = Integer.parseInt(y);
            int prefix = yy >= 70 ? 1900 : 2000;
            return String.valueOf(prefix + yy);
        }
        return y;
    }

    private ProductResponse toResponse(Product product) {
        long daysRemaining = -1;
        boolean expiringSoon = false;

        Optional<LocalDate> parsed = parseDate(product.getExpiryDate());
        if (parsed.isPresent()) {
            daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), parsed.get());
            expiringSoon = daysRemaining >= 0 && daysRemaining <= 7;
        }

        return new ProductResponse(
                product.getId(),
                product.getProductName(),
                product.getExtractedText(),
                product.getExpiryDate(),
                daysRemaining,
                expiringSoon
        );
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
