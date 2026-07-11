package com.example.demo.config;

import com.example.demo.sse.RedisSeatEventSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {
    public static final String SEAT_EVENTS_CHANNEL = "seat-events";

    @Bean
    public ChannelTopic seatEventsTopic() {
        return new ChannelTopic(SEAT_EVENTS_CHANNEL);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
        RedisConnectionFactory connectionFactory,
        RedisSeatEventSubscriber subscriber,
        ChannelTopic seatEventsTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, seatEventsTopic);
        return container;
    }
}
