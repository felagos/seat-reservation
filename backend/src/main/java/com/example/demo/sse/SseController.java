package com.example.demo.sse;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST endpoint for Server-Sent Events (SSE) subscription.
 * Clients open persistent HTTP connection to receive real-time seat updates.
 * Events: seat-held, seat-released, seat-reserved with seat and client info.
 */
@RestController
@RequestMapping("/api/seats/stream")
public class SseController {
    private final SseBroadcaster sseBroadcaster;

    /**
     * Constructor with broadcaster injection.
     *
     * @param sseBroadcaster the SSE event broadcaster
     */
    public SseController(SseBroadcaster sseBroadcaster) {
        this.sseBroadcaster = sseBroadcaster;
    }

    /**
     * Subscribe to seat event stream.
     * GET /api/seats/stream (text/event-stream)
     * Opens persistent connection that emits seat-held, seat-released, seat-reserved events.
     *
     * @return SseEmitter for client (connection held until disconnect)
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return sseBroadcaster.subscribe();
    }
}
