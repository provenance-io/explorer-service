package io.provenance.explorer.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.provenance.explorer.config.interceptor.JwtInterceptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class AppConfig : WebMvcConfigurer {
    @Autowired
    lateinit var objectMapper: ObjectMapper

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

    /**
     * Configure the jackson json mapper here in order for Spring to use the correct one. This needs
     * to be looked at closer in the future as the WebMvcConfigurer shouldn't be necessary.
     */
    override fun configureMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
        converters.add(MappingJackson2HttpMessageConverter(objectMapper))
        super.configureMessageConverters(converters)
    }
}
