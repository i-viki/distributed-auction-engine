package dev.jayavignesh.auction.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.jayavignesh.auction.dto.BidUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    public static final String AUCTION_BIDS_TOPIC = "auction:bids:broadcast";

    @Value("${spring.data.redis.listener.auto-startup:true}")
    private boolean redisListenerAutoStartup;

    @Bean
    public ChannelTopic auctionBidsTopic() {
        return new ChannelTopic(AUCTION_BIDS_TOPIC);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Redis Pub/Sub Message Listener:
     * Receives broadcasted bid events from any node in the cluster
     * and relays them to local STOMP WebSocket subscribers.
     */
    @Bean
    public RedisMessageListenerContainer redisContainer(
            RedisConnectionFactory connectionFactory,
            MessageListener redisMessageListener,
            ChannelTopic auctionBidsTopic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setAutoStartup(redisListenerAutoStartup);
        container.addMessageListener(redisMessageListener, auctionBidsTopic);
        return container;
    }

    @Bean
    public MessageListener redisMessageListener(
            SimpMessagingTemplate messagingTemplate) {

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        return (message, pattern) -> {
            try {
                byte[] body = message.getBody();
                BidUpdateMessage update = objectMapper.readValue(body, BidUpdateMessage.class);

                log.info("[Redis Pub/Sub] Received broadcast for Auction #{} (New Highest: ${})",
                        update.getAuctionId(), update.getCurrentHighestBid());

                // Relay to WebSocket STOMP subscribers on this node
                messagingTemplate.convertAndSend(
                        "/topic/auctions/" + update.getAuctionId(),
                        update
                );
            } catch (Exception e) {
                log.error("[Redis Pub/Sub] Failed to deserialize or relay bid message: {}", e.getMessage());
            }
        };
    }

    /**
     * Atomic In-Memory Bidding Lua Script:
     * Validates auction state, compares bid amount, applies anti-sniping extension,
     * and increments the atomic sequencer version in a single sub-millisecond atomic CPU cycle.
     * Externalized to src/main/resources/redis/atomic_bid_sequencer.lua
     */
    @Bean
    public DefaultRedisScript<List> atomicBiddingScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/atomic_bid_sequencer.lua"));
        script.setResultType(List.class);
        return script;
    }
}
