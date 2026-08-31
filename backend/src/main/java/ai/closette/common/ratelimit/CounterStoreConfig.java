package ai.closette.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Picks where the counters live.
 *
 * The choice is made at runtime rather than with {@code @ConditionalOnBean}: a
 * user configuration is parsed before Spring Boot's Redis auto-configuration has
 * registered anything, so the condition would never see the template and would
 * quietly pick the in-process store even with Redis running. Asking an
 * {@link ObjectProvider} at bean-creation time asks late enough to get a true
 * answer.
 */
@Configuration
public class CounterStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(CounterStoreConfig.class);

    @Bean
    public CounterStore counterStore(ObjectProvider<StringRedisTemplate> redis) {
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            log.warn("No Redis configured — rate limits are counted in-process and reset on restart");
            return new InMemoryCounterStore();
        }
        log.info("Rate limits counted in Redis");
        return new RedisCounterStore(template);
    }
}
