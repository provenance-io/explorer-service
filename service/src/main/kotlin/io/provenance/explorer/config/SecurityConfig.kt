package io.provenance.explorer.config

import io.provenance.core.extensions.toUuidProv
import io.provenance.explorer.config.filters.HeaderInterceptFilter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import java.util.*

@EnableConfigurationProperties(
        value = [ServiceProperties::class]
)
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(securedEnabled = true)
class SecurityConfig(val serviceProperties: ServiceProperties) : WebSecurityConfigurerAdapter() {

    @Throws(Exception::class)
    override fun configure(http: HttpSecurity) {
        http.sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .csrf().disable()
                .headers().frameOptions().disable()

        http.authorizeRequests()
                .antMatchers("/swagger*/**").permitAll()
                .antMatchers("/webjars/**").permitAll()
                .antMatchers("/v2/api-docs").permitAll()
                .antMatchers("/api/v1/**").permitAll()
                .anyRequest()
                .authenticated()
                .and()
                .addFilterBefore(HeaderInterceptFilter(), BasicAuthenticationFilter::class.java)
    }

    @Throws(Exception::class)
    override fun configure(webSecurity: WebSecurity) {
        webSecurity
                .ignoring()
                .antMatchers("/actuator/**")
    }


    @Throws(Exception::class)
    override fun authenticationManager(): AuthenticationManager {
        return ExplorerAuthenticationManager(serviceProperties)
    }
}

class ExplorerAuthentication(var uuid: UUID) : Authentication {
    private var authenticated = false

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> = mutableListOf()

    override fun setAuthenticated(isAuthenticated: Boolean) {
        authenticated = isAuthenticated
    }

    override fun getName(): String = uuid.toString()

    override fun getCredentials(): UUID = uuid

    override fun getPrincipal(): UUID = uuid

    override fun isAuthenticated(): Boolean = authenticated

    override fun getDetails(): UUID {
        return uuid
    }
}

class ExplorerAuthenticationManager(private val serviceProperties: ServiceProperties) : AuthenticationManager {
    override fun authenticate(authentication: Authentication?): Authentication {
        if (authentication == null) {
            throw BadCredentialsException("Invalid authentication object")
        }
        return try {
            val explorerAuthentication = authentication as ExplorerAuthentication

            if (explorerAuthentication.uuid.toString().isNullOrEmpty()) {
                throw BadCredentialsException("Invalid authentication object")
            }
            explorerAuthentication
        } catch (e: Exception) {
            throw BadCredentialsException("Invalid authentication object")
        }
    }
}
