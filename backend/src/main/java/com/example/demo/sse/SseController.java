package com.example.demo.sse;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events/{eventId}/stream")
@CrossOrigin(origins = "*", maxAge = 3600)
public class SseController {
    private final SseBroadcaster sseBroadcaster;

    public SseController(SseBroadcaster sseBroadcaster) {
        this.sseBroadcaster = sseBroadcaster;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable Long eventId) {
        return sseBroadcaster.subscribe(eventId);
    }
}
