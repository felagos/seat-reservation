package com.example.demo.sse;

import com.example.demo.config.RedisConfig;
import com.example.demo.event.SeatHeldEvent;
import com.example.demo.event.SeatReleasedEvent;
import com.example.demo.event.SeatReservedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SeatEventListener {
    private static final Logger log = LoggerFactory.getLogger(SeatEventListener.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final StringRedisTemplate redisTemplate;

    public SeatEventListener(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatHeld(SeatHeldEvent event) {
        publish(event.eventId(), "seat-held", new SeatHeldPayload(
            event.seatId(),
            event.heldBy(),
            event.expiresAt()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatReleased(SeatReleasedEvent event) {
        publish(event.eventId(), "seat-released", new SeatReleasedPayload(
            event.seatId()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatReserved(SeatReservedEvent event) {
        publish(event.eventId(), "seat-reserved", new SeatReservedPayload(
            event.seatId(),
            event.reservationId()
        ));
    }

    private void publish(Long eventId, String eventName, Object payload) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(new SeatEventMessage(eventId, eventName, payload));
            redisTemplate.convertAndSend(RedisConfig.SEAT_EVENTS_CHANNEL, json);
        } catch (Exception e) {
            log.warn("Failed to publish seat event to Redis: {}", e.getMessage());
        }
    }

    public record SeatHeldPayload(Long seatId, String heldBy, java.time.Instant expiresAt) {}
    public record SeatReleasedPayload(Long seatId) {}
    public record SeatReservedPayload(Long seatId, Long reservationId) {}
}
