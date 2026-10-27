package com.akanksha.url_shortener.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RateLimitConfig {

    @Bean
    @ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            StringRedisTemplate redis,
            @Value("${app.rate-limit.create-per-minute:10}") int createLimit,
            @Value("${app.rate-limit.read-per-minute:120}") int readLimit) {

        FilterRegistrationBean<RateLimitFilter> reg =
                new FilterRegistrationBean<>(new RateLimitFilter(redis, createLimit, readLimit));
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return reg;
    }
}