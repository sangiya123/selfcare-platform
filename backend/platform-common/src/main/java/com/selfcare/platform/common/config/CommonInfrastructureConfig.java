package com.selfcare.platform.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.selfcare.platform.common.context.RequestContextTaskDecorator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/** Shared beans used by services that do not otherwise enable the related auto-configuration. */
@Configuration
public class CommonInfrastructureConfig {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    @ConditionalOnMissingBean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    @ConditionalOnMissingBean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(redisJsonSerializer());
        template.setHashValueSerializer(redisJsonSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Generic JSON Redis serializer with class metadata for polymorphic reads.
     * The mapper carries the Java 8 date/time (JSR-310) module so cached values
     * containing {@link java.time.Instant} (e.g. compiled manifests) serialize cleanly.
     */
    private GenericJackson2JsonRedisSerializer redisJsonSerializer() {
        ObjectMapper mapper = objectMapper().copy()
                .activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                        ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Bean(RequestContextTaskDecorator.BEAN_NAME)
    @ConditionalOnMissingBean(name = RequestContextTaskDecorator.BEAN_NAME)
    public RequestContextTaskDecorator requestContextTaskDecorator() {
        return new RequestContextTaskDecorator();
    }
}
