package com.expirytracker.service;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.expirytracker.dto.ProductRequest;
import com.expirytracker.dto.ProductResponse;
import com.expirytracker.entity.Product;
import com.expirytracker.entity.User;
import com.expirytracker.ocr.OCRService;
import com.expirytracker.repository.ProductRepository;
import com.expirytracker.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private UserRepository userRepository;

    private ProductService productService;

    private static Stream<Arguments> supportedDirectInputFormats() {
        return Stream.of(
                Arguments.of("25/12/2026", "25/12/2026"),
                Arguments.of("2026/12", "2026/12"),
                Arguments.of("12/2026", "12/2026"),
                Arguments.of("Feb 2026", "02/2026"),
                Arguments.of("2026", "2026"),
                Arguments.of("BEST BEFORE 07/2027", "07/2027")
        );
    }

    private static Stream<Arguments> supportedOcrExtractedTextFormats() {
        return Stream.of(
                Arguments.of("EXP 25/12/2026", "25/12/2026"),
                Arguments.of("PACK DATE 2024/11 EXP", "2024/11"),
                Arguments.of("USE BY 08/2027", "08/2027"),
                Arguments.of("BEST BEFORE FEB 2026", "02/2026"),
                Arguments.of("MFG 2024 EXP 2026", "2026"),
                Arguments.of("USE BY: 03-11-2026", "03/11/2026"),
                Arguments.of("MFG: 14/07/2024 BB: 14/07/2026", "14/07/2026"),
                Arguments.of("MFG 09/05/2018 EXP 08/05/2020", "08/05/2020"),
                Arguments.of("EXP 04.2027", "04/2027"),
                Arguments.of("BB|14|07|2026", "14/07/2026"),
                Arguments.of("BEST BEFORE 14 - 07 - 2026", "14/07/2026"),
                Arguments.of("EXP 2026/07/14", "2026/07/14")
        );
    }

    private static Stream<String> ocrTypedProductNames() {
        return Stream.of("bread", "milk", "toothpaste", "eggs");
    }

    @BeforeEach
    void setUp() {
        GoogleVisionService googleVisionService = new GoogleVisionService();
        OCRService ocrService = new OCRService();
        productService = new ProductService(repository, userRepository, googleVisionService, ocrService);

        User user = new User();
        user.setName("Alice");
        user.setEmail("alice@example.com");
        user.setPassword("secret");

        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    @ParameterizedTest
    @MethodSource("supportedDirectInputFormats")
    void addProductShouldNormalizeAllSupportedDirectInputFormats(String input, String expected) {
        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductRequest request = new ProductRequest();
        request.setProductName("Milk");
        request.setExpiryDate(input);

        ProductResponse response = productService.addProduct(1L, request);

        assertEquals(expected, response.getExpiryDate());
        assertEquals("Manual entry", response.getExtractedText());
    }

    @ParameterizedTest
    @MethodSource("supportedOcrExtractedTextFormats")
    void extractExpiryDateShouldSupportAllFormatsFromOcrText(String extractedText, String expected) throws Exception {
        assertEquals(expected, invokeExtractExpiryDate(extractedText));
    }

    @ParameterizedTest
    @MethodSource("ocrTypedProductNames")
    void processProductShouldSaveAnyUserEnteredNameForImageUpload(String typedProductName) {
        ProductService service = new ProductService(
                repository,
                userRepository,
                new GoogleVisionService() {
                    @Override
                    public String extractTextFromImage(java.io.File file) {
                        return "EXP 14/07/2026";
                    }
                },
                new OCRService() {
                    @Override
                    public String extractText(java.io.File file) {
                        return "";
                    }
                }
        );

        when(repository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "month year.jpg",
                "image/jpeg",
                "dummy-content".getBytes()
        );

        ProductResponse response = service.processProduct(1L, typedProductName, file);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(repository).save(captor.capture());

        assertEquals(typedProductName, captor.getValue().getProductName());
        assertEquals(typedProductName, response.getProductName());
        assertEquals("14/07/2026", response.getExpiryDate());
    }

        @Test
        void processProductShouldRejectBlankTypedNameForImageUpload() {
        ProductService service = new ProductService(
            repository,
            userRepository,
            new GoogleVisionService(),
            new OCRService()
        );

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "month year.jpg",
            "image/jpeg",
            "dummy-content".getBytes()
        );

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.processProduct(1L, "   ", file)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }

    @Test
    void addProductShouldRejectUnsupportedDirectInputFormat() {
        ProductRequest request = new ProductRequest();
        request.setProductName("Milk");
        request.setExpiryDate("tomorrow evening");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> productService.addProduct(1L, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private String invokeExtractExpiryDate(String text) throws Exception {
        Method method = ProductService.class.getDeclaredMethod("extractExpiryDate", String.class);
        method.setAccessible(true);
        return (String) method.invoke(productService, text);
    }
}
