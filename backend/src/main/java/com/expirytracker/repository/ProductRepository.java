package com.expirytracker.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.expirytracker.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
	List<Product> findByUserIdOrderByIdDesc(Long userId);
	Optional<Product> findByIdAndUserId(Long id, Long userId);

	@Query("SELECT p FROM Product p JOIN FETCH p.user")
	List<Product> findAllWithUser();
}