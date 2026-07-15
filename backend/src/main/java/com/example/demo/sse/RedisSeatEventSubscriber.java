package com.example.demo.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.stereotype.Component;

@Component
public class RedisSeatEventSubscriber implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(RedisSeatEventSubscriber.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final SseBroadcaster sseBroadcaster;

    public RedisSeatEventSubscriber(SseBroadcaster sseBroadcaster) {
        this.sseBroadcaster = sseBroadcaster;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            SeatEventMessage event = OBJECT_MAPPER.readValue(message.getBody(), SeatEventMessage.class);
            sseBroadcaster.broadcast(event.eventName(), event.payload());
        } catch (Exception e) {
            log.warn("Failed to process seat event from Redis: {}", e.getMessage());
        }
    }
}
