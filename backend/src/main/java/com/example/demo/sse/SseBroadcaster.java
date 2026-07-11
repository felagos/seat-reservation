package com.example.demo.sse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseBroadcaster {
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emittersByEventId = new ConcurrentHashMap<>();

    public SseBroadcaster() {
    }

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

    public void sendHeartbeatToAll() {
        for (Long eventId : emittersByEventId.keySet()) {
            sendHeartbeat(eventId);
        }
    }
}
