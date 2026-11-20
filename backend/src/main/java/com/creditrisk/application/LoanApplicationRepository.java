package com.creditrisk.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    List<LoanApplication> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<LoanApplication> findByIdAndUserId(Long id, Long userId);
}
