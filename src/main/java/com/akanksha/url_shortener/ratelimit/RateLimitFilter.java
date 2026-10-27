package com.akanksha.url_shortener.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final long WINDOW_MS = 60_000;
    private static final Pattern CODE_PATH = Pattern.compile("^/[a-zA-Z0-9_-]+$");

    // INCR + set expiry on first hit, in one atomic step. Returns {count, ttlMs}
    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
            local c = redis.call('INCR', KEYS[1])
            if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return {c, redis.call('PTTL', KEYS[1])}
            """, List.class);

    private record Rule(String name, int limit) {}

    private final StringRedisTemplate redis;
    private final int createLimit;
    private final int readLimit;

    public RateLimitFilter(StringRedisTemplate redis, int createLimit, int readLimit) {
        this.redis = redis;
        this.createLimit = createLimit;
        this.readLimit = readLimit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        Rule rule = ruleFor(req);
        if (rule == null) {                       // static files etc. are not limited
            chain.doFilter(req, res);
            return;
        }

        long count;
        long ttlMs;
        try {
            List<?> r = redis.execute(SCRIPT,
                    List.of("rl:" + rule.name() + ":" + req.getRemoteAddr()),
                    String.valueOf(WINDOW_MS));
            count = ((Number) r.get(0)).longValue();
            ttlMs = ((Number) r.get(1)).longValue();
        } catch (RuntimeException e) {            // Redis down: fail open
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
            chain.doFilter(req, res);
            return;
        }

        res.setHeader("X-RateLimit-Limit", String.valueOf(rule.limit()));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, rule.limit() - count)));

        if (count > rule.limit()) {
            long retryAfter = Math.max(1, (ttlMs + 999) / 1000);
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(retryAfter));
            res.setContentType("application/json");
            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
            res.getWriter().write("{\"timestamp\":\"" + Instant.now()
                    + "\",\"status\":429,\"error\":\"Too many requests. Try again in "
                    + retryAfter + " seconds.\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    private Rule ruleFor(HttpServletRequest req) {
        String path = req.getRequestURI();
        String method = req.getMethod();
        if ("POST".equals(method) && "/api/urls".equals(path)) {
            return new Rule("create", createLimit);
        }
        if ("GET".equals(method) && (CODE_PATH.matcher(path).matches() || path.startsWith("/api/urls/"))) {
            return new Rule("read", readLimit);
        }
        return null;
    }
}