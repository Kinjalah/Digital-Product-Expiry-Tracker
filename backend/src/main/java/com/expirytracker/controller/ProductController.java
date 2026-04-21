package com.expirytracker.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.expirytracker.dto.ProductRequest;
import com.expirytracker.dto.ProductResponse;
import com.expirytracker.service.ProductService;

@RestController
@RequestMapping("/product")
@CrossOrigin(origins = "http://localhost:5173")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping("/upload")
    public ResponseEntity<ProductResponse> uploadProduct(
            @RequestParam("userId") Long userId,
            @RequestParam("productName") String productName,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(service.processProduct(userId, productName, file));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> addProduct(
            @RequestParam("userId") Long userId,
            @RequestBody ProductRequest request
    ) {
        return ResponseEntity.ok(service.addProduct(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getProducts(@RequestParam("userId") Long userId) {
        return ResponseEntity.ok(service.getProducts(userId));
    }

    @GetMapping("/expiring")
    public ResponseEntity<List<ProductResponse>> getExpiringProducts(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "days", defaultValue = "7") int days
    ) {
        return ResponseEntity.ok(service.getExpiringProducts(userId, days));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @RequestParam("userId") Long userId,
            @RequestBody ProductRequest request
    ) {
        return ResponseEntity.ok(service.updateProduct(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteProduct(
            @PathVariable Long id,
            @RequestParam("userId") Long userId
    ) {
        service.deleteProduct(userId, id);
        return ResponseEntity.ok(Map.of("message", "Product deleted"));
    }
}
