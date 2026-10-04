package com.memorygames.pk.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/** Persists audit rows; never allowed to break the admin call itself. */
@Service
public class AdminAuditService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);

    private final AdminAuditRepository repository;

    public AdminAuditService(AdminAuditRepository repository) {
        this.repository = repository;
    }

    public void record(String action, String params, String ip, String result) {
        try {
            AdminAudit row = new AdminAudit();
            row.ts = System.currentTimeMillis();
            row.action = action;
            row.paramsJson = params == null ? "" : params;
            row.operatorIp = ip == null ? "" : ip;
            row.result = result == null ? "ok" : result;
            this.repository.save(row);
        } catch (RuntimeException e) {
            log.warn("audit persist failed for {}: {}", action, e.getMessage());
        }
    }

    public List<AdminAudit> recent(int n) {
        return this.repository.findAllByOrderByTsDesc(PageRequest.of(0, Math.max(1, Math.min(n, 200))));
    }
}
