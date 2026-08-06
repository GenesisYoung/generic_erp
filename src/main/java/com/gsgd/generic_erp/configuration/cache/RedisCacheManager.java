package com.gsgd.generic_erp.configuration.cache;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class RedisCacheManager {
    @Bean
    public CacheManager cacheManager() {
        var manager = new ConcurrentMapCacheManager("userQuery");
        return manager;
    }

}
