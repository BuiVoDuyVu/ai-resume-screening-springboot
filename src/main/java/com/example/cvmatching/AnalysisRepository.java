package com.example.cvmatching;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnalysisRepository extends JpaRepository<AnalysisEntity, UUID> {
    List<AnalysisEntity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
