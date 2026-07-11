package com.example.demo.sse;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class SseHeartbeatScheduler {
    private final SseBroadcaster sseBroadcaster;

    public SseHeartbeatScheduler(SseBroadcaster sseBroadcaster) {
        this.sseBroadcaster = sseBroadcaster;
    }

    @Scheduled(fixedRate = 15000)
    public void sendHeartbeats() {
        sseBroadcaster.sendHeartbeatToAll();
    }
}
