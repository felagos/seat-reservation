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

/**
 * Transactional event listener for seat domain events.
 * Receives SeatHeldEvent, SeatReleasedEvent, SeatReservedEvent after transaction commit.
 * Publishes to Redis Pub/Sub for cross-instance SSE fanout (SseBroadcaster on all backends).
 * Serializes events as JSON with ISO 8601 timestamps.
 */
@Component
public class SeatEventListener {
    private static final Logger log = LoggerFactory.getLogger(SeatEventListener.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final StringRedisTemplate redisTemplate;

    /**
     * Constructor with Redis template injection.
     *
     * @param redisTemplate Spring Redis template for Pub/Sub
     */
    public SeatEventListener(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Handle SeatHeldEvent after transaction commit.
     * Publishes to Redis channel "seat-events" for SSE broadcast to all connected clients.
     *
     * @param event the seat held domain event
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatHeld(SeatHeldEvent event) {
        publish(event.eventId(), "seat-held", new SeatHeldPayload(
            event.seatId(),
            event.heldBy(),
            event.expiresAt()
        ));
    }

    /**
     * Handle SeatReleasedEvent after transaction commit.
     * Publishes to Redis channel "seat-events" for SSE broadcast.
     *
     * @param event the seat released domain event
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatReleased(SeatReleasedEvent event) {
        publish(event.eventId(), "seat-released", new SeatReleasedPayload(
            event.seatId()
        ));
    }

    /**
     * Handle SeatReservedEvent after transaction commit.
     * Publishes to Redis channel "seat-events" for SSE broadcast.
     *
     * @param event the seat reserved domain event
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatReserved(SeatReservedEvent event) {
        publish(event.eventId(), "seat-reserved", new SeatReservedPayload(
            event.seatId(),
            event.reservationId()
        ));
    }

    /**
     * Publish event message to Redis Pub/Sub for cross-instance fanout.
     * Logs warnings if serialization fails but doesn't throw (non-blocking).
     *
     * @param eventId event ID (part of message)
     * @param eventName "seat-held", "seat-released", or "seat-reserved"
     * @param payload event-specific payload (seatId, heldBy, expiresAt, etc.)
     */
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
