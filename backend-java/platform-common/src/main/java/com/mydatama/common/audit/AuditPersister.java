package com.mydatama.common.audit;

import java.util.List;

/**
 * 审计落库接口，由 module-iam 实现（写 iam.audit_logs）。
 */
public interface AuditPersister {

    void saveBatch(List<AuditEvent> events);
}
