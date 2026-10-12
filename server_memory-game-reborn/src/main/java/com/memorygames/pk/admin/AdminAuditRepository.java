package com.memorygames.pk.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditRepository extends JpaRepository<AdminAudit, Long> {

    List<AdminAudit> findAllByOrderByTsDesc(Pageable pageable);
}
