package com.octapulse.backend.config;

import com.octapulse.backend.repository.UserRepository;
import com.octapulse.backend.security.AdminInterceptor;
import com.octapulse.backend.security.AuthInterceptor;
import com.octapulse.backend.security.JwtService;
import com.octapulse.backend.security.RateLimitInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final int authRateLimit;
    private final String[] corsOrigins;

    public WebConfig(
            JwtService jwtService,
            UserRepository userRepository,
            @Value("${rate-limit.auth-per-minute:20}") int authRateLimit,
            @Value("${app.cors-origins:}") String[] corsOrigins
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.authRateLimit = authRateLimit;
        this.corsOrigins = corsOrigins;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(authRateLimit, 60_000))
                .addPathPatterns("/auth/**");
        // Interceptors run in registration order: optional auth everywhere, then the required checks.
        registry.addInterceptor(new AuthInterceptor(jwtService, false))
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**", "/health", "/v3/api-docs/**", "/swagger-ui/**");
        registry.addInterceptor(new AuthInterceptor(jwtService, true))
                .addPathPatterns("/picks/**", "/me/**", "/leagues/**", "/admin/**", "/auth/logout-all");
        registry.addInterceptor(new AdminInterceptor(userRepository))
                .addPathPatterns("/admin/**");
        registry.addInterceptor(new CatalogCacheInterceptor())
                .addPathPatterns("/events/**", "/fighters/**", "/rankings/**", "/news/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (corsOrigins.length > 0) {
            registry.addMapping("/**")
                    .allowedOrigins(corsOrigins)
                    .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                    .exposedHeaders("ETag", "Retry-After");
        }
    }

    /** Adds ETags to GET responses so clients can revalidate with If-None-Match and get 304s. */
    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> bean = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        bean.addUrlPatterns("/events", "/events/*", "/fighters", "/fighters/*", "/rankings", "/leaderboard",
                "/fights/*", "/meta/*", "/news");
        return bean;
    }

    /** Catalog data changes at most every few minutes, so let clients and CDNs reuse it briefly. */
    private static class CatalogCacheInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if ("GET".equals(request.getMethod())) {
                response.setHeader("Cache-Control", "public, max-age=60");
            }
            return true;
        }
    }
}
