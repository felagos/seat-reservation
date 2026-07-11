package com.example.demo.sse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-Sent Events broadcaster for seat updates.
 * Holds live SseEmitter connections per event ID, thread-safe.
 * Receives events from RedisSeatEventSubscriber and broadcasts to local clients.
 * Per-JVM (in-memory) — does not share connections across backend instances.
 */
@Component
public class SseBroadcaster {
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emittersByEventId = new ConcurrentHashMap<>();

    /**
     * Default constructor.
     */
    public SseBroadcaster() {
    }

    /**
     * Subscribe client to seat event stream for event.
     * Registers cleanup callbacks for completion, timeout, error.
     *
     * @param eventId event ID to subscribe to
     * @return SseEmitter for HTTP client to receive events
     */
    public SseEmitter subscribe(Long eventId) {
        SseEmitter emitter = new SseEmitter(0L);
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByEventId
            .computeIfAbsent(eventId, key -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(throwable -> emitters.remove(emitter));

        return emitter;
    }

    /**
     * Broadcast seat event to all SSE clients subscribed to event.
     * Silently removes emitters on send failure (client disconnected).
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
                emitters.remove(emitter);
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
                emitters.remove(emitter);
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
}
