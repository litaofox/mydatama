package com.mydatama.common.audit;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 审计事件内存队列：有界，满则丢弃（审计失败不影响主流程）。
 */
@Component
public class AuditSink {

    private static final int MAX_QUEUE = 10000;

    private final ConcurrentLinkedQueue<AuditEvent> queue = new ConcurrentLinkedQueue<>();

    public void offer(AuditEvent event) {
        if (event != null && queue.size() < MAX_QUEUE) {
            queue.add(event);
        }
    }

    public List<AuditEvent> drain(int max) {
        List<AuditEvent> batch = new ArrayList<>();
        AuditEvent e;
        while (batch.size() < max && (e = queue.poll()) != null) {
            batch.add(e);
        }
        return batch;
    }
}
