package com.mydatama.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 每 2 秒批量落库审计事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditFlusher {

    private final AuditSink sink;
    private final ObjectProvider<AuditPersister> persisterProvider;

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void flush() {
        AuditPersister persister = persisterProvider.getIfAvailable();
        if (persister == null) {
            return;
        }
        List<AuditEvent> batch = sink.drain(200);
        if (batch.isEmpty()) {
            return;
        }
        try {
            persister.saveBatch(batch);
        } catch (Exception e) {
            log.warn("audit batch save failed, dropped {} events: {}", batch.size(), e.getMessage());
        }
    }
}
