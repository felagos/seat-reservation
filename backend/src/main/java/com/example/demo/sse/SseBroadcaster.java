package com.example.demo.sse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Server-Sent Events broadcaster for seat updates.
 * Holds live SseEmitter connections per event ID, thread-safe.
 * Receives events from RedisSeatEventSubscriber and broadcasts to local clients.
 * Per-JVM (in-memory) — does not share connections across backend instances.
 */
@Component
public class SseBroadcaster {
    /** Well above the 15s heartbeat interval; bounds how long a dead-but-unnoticed connection lingers. */
    private static final long EMITTER_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emittersByEventId = new ConcurrentHashMap<>();

    /**
     * Subscribe client to seat event stream for event.
     * Registers cleanup callbacks for completion, timeout, error.
     *
     * @param eventId event ID to subscribe to
     * @return SseEmitter for HTTP client to receive events
     */
    public SseEmitter subscribe(Long eventId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByEventId
            .computeIfAbsent(eventId, key -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(eventId, emitter));
        emitter.onTimeout(() -> removeEmitter(eventId, emitter));
        emitter.onError(throwable -> removeEmitter(eventId, emitter));

        return emitter;
    }

    /**
     * Broadcast seat event to all SSE clients subscribed to event.
     * Completes the emitter with the error on send failure (client disconnected), which
     * triggers onError cleanup instead of leaving a dead connection registered.
     *
     * @param eventId event ID
     * @param eventName event type ("seat-held", "seat-released", "seat-reserved")
     * @param payload event data as JSON
     */
    public void broadcast(Long eventId, String eventName, Object payload) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByEventId.get(eventId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

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
     * Send heartbeat comment to all clients of event (prevents proxy timeout).
     *
     * @param eventId event ID
     */
    public void sendHeartbeat(Long eventId) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByEventId.get(eventId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }
    }

    /**
     * Send heartbeat to all events (called by SseHeartbeatScheduler).
     */
    public void sendHeartbeatToAll() {
        for (Long eventId : emittersByEventId.keySet()) {
            sendHeartbeat(eventId);
        }
    }

    private void removeEmitter(Long eventId, SseEmitter emitter) {
        emittersByEventId.computeIfPresent(eventId, (key, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
