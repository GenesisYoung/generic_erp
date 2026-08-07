package com.gsgd.generic_erp.redis;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration
@EnableCaching
public class RedisConfiguration {

        @Value("${REDIS_HOST:localhost}")
        private String redisHost;
        @Value("${REDIS_PORT:6379}")
        private int redisPort;

        /**
         * Simple redis template configurations
         */
        @Bean
        public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
                RedisTemplate<String, Object> template = new RedisTemplate<>();
                template.setConnectionFactory(redisConnectionFactory);
                template.setKeySerializer(new StringRedisSerializer());
                template.setValueSerializer(new StringRedisSerializer());
                return template;
        }

        /**
         * Redis caching configurations
         */
        @Bean
        public RedisCacheConfiguration defaultCacheConfig() {
                GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer.builder()
                                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                                                .allowIfSubType("com.gsgd.generic_erp.")
                                                .allowIfSubType("java.util.")
                                                .allowIfSubType("java.lang.")
                                                .build())
                                .typePropertyName("@class")
                                .enableSpringCacheNullValueSupport()
                                .build();

                return RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(30))
                                .computePrefixWith(name -> "erp:cache:" + name + ":")
                                .serializeKeysWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(serializer));
        }

        /**
         * Different TTL per cache name.
         * If there are more cache needed, just add caches here
         */
        @Bean
        public RedisCacheManagerBuilderCustomizer cacheCustomizer(
                        RedisCacheConfiguration base) {
                return builder -> builder
                                .withCacheConfiguration("userQuery", base.entryTtl(Duration.ofDays(7)));
        }

}
