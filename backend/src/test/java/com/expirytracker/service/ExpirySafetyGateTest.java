package com.expirytracker.service;

import com.expirytracker.dto.ProductRequest;
import com.expirytracker.entity.Product;
import com.expirytracker.entity.User;
import com.expirytracker.ocr.OCRService;
import com.expirytracker.repository.ProductRepository;
import com.expirytracker.repository.UserRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** FA2 domain gate: tests the actual service, including invalid date rejection. */
@ExtendWith(MockitoExtension.class)
class ExpirySafetyGateTest {
    @Mock ProductRepository products;
    @Mock UserRepository users;
    ProductService service;
    @BeforeEach void setup() {
        service = new ProductService(products, users, new GoogleVisionService(), new OCRService());
    }
    @ParameterizedTest
    @CsvSource({"-1,false", "0,true", "1,true", "7,true", "8,false"})
    void sevenDayWindowIncludesTodayAndExcludesExpiredItems(int offset, boolean expected) {
        Product p = new Product();
        p.setProductName("Boundary item");
        p.setExpiryDate(LocalDate.now().plusDays(offset).format(DateTimeFormatter.ofPattern("dd/MM/uuuu")));
        when(products.findByUserIdOrderByIdDesc(1L)).thenReturn(List.of(p));
        var result = service.getProducts(1L).get(0);
        assertEquals(offset, result.getDaysRemaining());
        assertEquals(expected, result.isExpiringSoon());
        assertEquals(expected ? 1 : 0, service.getExpiringProducts(1L, 7).size());
    }
    @ParameterizedTest
    @ValueSource(strings={"31/02/2027", "29/02/2027", "32/12/2027", "10/13/2027", "tomorrow"})
    void impossibleDatesCannotBeSaved(String date) {
        when(users.findById(1L)).thenReturn(Optional.of(new User()));
        ProductRequest request = new ProductRequest();
        request.setProductName("Milk"); request.setExpiryDate(date);
        assertThrows(ResponseStatusException.class, () -> service.addProduct(1L, request));
        verify(products, never()).save(any());
    }
    @Test void leapDayCanBeSaved() {
        when(users.findById(1L)).thenReturn(Optional.of(new User()));
        when(products.save(any())).thenAnswer(i -> i.getArgument(0));
        ProductRequest request = new ProductRequest();
        request.setProductName("Milk"); request.setExpiryDate("29/02/2028");
        assertEquals("29/02/2028", service.addProduct(1L, request).getExpiryDate());
    }
}
