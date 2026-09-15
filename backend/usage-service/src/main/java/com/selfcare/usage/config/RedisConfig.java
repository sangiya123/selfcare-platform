package com.selfcare.usage.config;

import com.selfcare.platform.common.adapter.BalanceProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis template beans for balance and usage caching.
 *
 * Two distinct generic bean types so that {@code RedisTemplate<String, Balance>}
 * and {@code RedisTemplate<String, UsageSummary>} can be injected without ambiguity.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, BalanceProvider.Balance> balanceCacheTemplate(RedisConnectionFactory factory) {
        return buildTemplate(factory);
    }

    @Bean
    public RedisTemplate<String, BalanceProvider.UsageSummary> usageCacheTemplate(RedisConnectionFactory factory) {
        return buildTemplate(factory);
    }

    private <T> RedisTemplate<String, T> buildTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, T> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}