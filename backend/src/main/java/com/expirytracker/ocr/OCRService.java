package com.expirytracker.ocr;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import net.sourceforge.tess4j.Tesseract;

@Service
public class OCRService {

    public String extractText(File file) {
        try {
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata");

            // ✅ OCR tuning
            tesseract.setPageSegMode(6);
            tesseract.setOcrEngineMode(3);
            tesseract.setTessVariable("tessedit_char_whitelist",
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789:/-. ");
            tesseract.setTessVariable("user_defined_dpi", "300");

            BufferedImage original = ImageIO.read(file);

            String bestText = "";

            // ✅ MULTIPLE SAFE PIPELINES (NO OVERPROCESSING)
            BufferedImage[] versions = new BufferedImage[]{

                    original,                        // 🔥 MOST IMPORTANT
                    resize(original, 2),
                    toGray(original),
                    threshold(toGray(original))
            };

            int[] angles = {0, 90, 180, 270};

            for (BufferedImage img : versions) {
                for (int angle : angles) {

                    BufferedImage rotated = rotate(img, angle);

                    String text = tesseract.doOCR(rotated);
                    String normalizedText = normalizeCandidate(text);

                    System.out.println("OCR OUTPUT:\n" + text);

                    // Accept multiline OCR output when it contains at least one digit.
                    if (containsDigit(normalizedText)) {

                        if (isBetter(normalizedText, bestText)) {
                            bestText = normalizedText;
                        }
                    }
                }
            }

            System.out.println("FINAL BEST OCR:\n" + bestText);

            return bestText;

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // ✅ ROTATE
    private BufferedImage rotate(BufferedImage img, double angle) {
        int w = img.getWidth();
        int h = img.getHeight();

        BufferedImage rotated = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rotated.createGraphics();

        g.rotate(Math.toRadians(angle), w / 2, h / 2);
        g.drawImage(img, 0, 0, null);
        g.dispose();

        return rotated;
    }

    // ✅ RESIZE
    private BufferedImage resize(BufferedImage img, int scale) {
        int w = img.getWidth() * scale;
        int h = img.getHeight() * scale;

        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();

        return resized;
    }

    // ✅ GRAYSCALE
    private BufferedImage toGray(BufferedImage img) {
        BufferedImage gray = new BufferedImage(
                img.getWidth(),
                img.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );

        Graphics g = gray.getGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();

        return gray;
    }

    // ✅ SOFT THRESHOLD (NOT AGGRESSIVE)
    private BufferedImage threshold(BufferedImage img) {

        BufferedImage out = new BufferedImage(
                img.getWidth(),
                img.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY
        );

        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {

                int pixel = img.getRGB(x, y) & 0xFF;

                int value = (pixel > 130) ? 255 : pixel;

                int newPixel = (value << 16) | (value << 8) | value;

                out.setRGB(x, y, newPixel);
            }
        }

        return out;
    }

    // ✅ SELECT BEST TEXT
    private boolean isBetter(String newText, String oldText) {

        if (newText == null || newText.trim().isEmpty()) return false;

        return score(newText) > score(oldText);
    }

    private String normalizeCandidate(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private boolean containsDigit(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    // ✅ SCORING FUNCTION (IMPORTANT)
    private int score(String text) {

        if (text == null) return 0;

        int score = 0;

        String t = text.toUpperCase();

        // 🔥 Strong boost for date patterns
        if (t.matches(".*\\d{2}.*\\d{2}.*\\d{4}.*")) score += 100;
        if (t.matches(".*\\d{4}.*")) score += 50;

        // 🔥 Keyword boost
        if (t.contains("EXP")) score += 40;
        if (t.contains("BEST")) score += 30;
        if (t.contains("USE")) score += 20;

        // 🔥 Penalize garbage (only letters, no numbers)
        if (!containsDigit(t)) score -= 50;

        // Length bonus
        score += t.length();

        return score;
    }
}