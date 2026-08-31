package ai.closette.common.ratelimit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Hangs the limiter on every API request. */
@Configuration
public class RateLimitWebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor interceptor;

    public RateLimitWebConfig(RateLimitInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**");
    }
}
