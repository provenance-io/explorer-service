package io.provenance.explorer.config

import io.provenance.explorer.config.interceptor.JwtInterceptor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class AppConfig : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.let { super.addInterceptors(it) }
        registry.addInterceptor(JwtInterceptor()).excludePathPatterns(
            "/external/**",
            "/swagger*/**",
            "/webjars/**",
            "/v2/api-docs*",
            "/v3/api-docs*"
        )
    }
}

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = ["is-under-maintenance"], prefix = "explorer", havingValue = "false")
class SchedulerConfig
