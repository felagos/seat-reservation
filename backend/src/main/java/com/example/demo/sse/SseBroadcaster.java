package com.example.demo.sse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Server-Sent Events broadcaster for seat updates.
 * Holds live SseEmitter connections, thread-safe.
 * Receives events from RedisSeatEventSubscriber and broadcasts to local clients.
 * Per-JVM (in-memory) — does not share connections across backend instances.
 */
@Component
public class SseBroadcaster {
    /** Well above the 15s heartbeat interval; bounds how long a dead-but-unnoticed connection lingers. */
    private static final long EMITTER_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Subscribe client to seat event stream.
     * Registers cleanup callbacks for completion, timeout, error.
     *
     * @return SseEmitter for HTTP client to receive events
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(emitter));
        emitter.onTimeout(() -> removeEmitter(emitter));
        emitter.onError(throwable -> removeEmitter(emitter));

        return emitter;
    }

    /**
     * Broadcast seat event to all SSE clients.
     * Completes the emitter with the error on send failure (client disconnected), which
     * triggers onError cleanup instead of leaving a dead connection registered.
     *
     * @param eventName event type ("seat-held", "seat-released", "seat-reserved")
     * @param payload event data as JSON
     */
    public void broadcast(String eventName, Object payload) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }
    }

    /**
     * Send heartbeat comment to all clients (prevents proxy timeout).
     */
    public void sendHeartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }
    }

    private void removeEmitter(SseEmitter emitter) {
        emitters.remove(emitter);
    }
}
