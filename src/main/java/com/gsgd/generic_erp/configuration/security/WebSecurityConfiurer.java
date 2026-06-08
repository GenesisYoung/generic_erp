package com.gsgd.generic_erp.configuration.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class WebSecurityConfiurer {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    WebSecurityConfiurer(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    SecurityFilterChain filterChain(org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
        http.cors(cors->cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf->csrf.disable())
        .sessionManagement(sm->sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests((authz) -> authz
            // Allow unauthenticated access to login and registration endpoints
                .requestMatchers("/api/auth/login", "/api/auth/register")
                .permitAll()
                // All other endpoints require authentication
                .anyRequest()
                .authenticated()
            )
            // Add the JWT authentication filter before the default username/password filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // CORS configuration to allow requests from the frontend application
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Use a PATTERN, not "*", so credentials are allowed:
        config.setAllowedOriginPatterns(List.of("http://localhost:*"));
        // Allow all standard HTTP methods
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // Allow all headers
        config.setAllowedHeaders(List.of("*"));
        // Allow credentials (cookies, authorization headers, etc.) to be included in requests
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply this CORS configuration to all endpoints
        source.registerCorsConfiguration("/**", config);
        return source;
    }
    // Configure the authentication provider to use our custom
    // UserDetailsService and password encoder(CustomizedUserDetailServiceImpl)
    @Bean
    public AuthenticationProvider authenticationProvider(
        UserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder) {
        //This userDetailsService is the CustomizedUserDetailServiceImpl class 
        //that we created to load user details from the database.
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    // Configure the authentication manager to use our custom authentication provider
    @Bean
    AuthenticationManager authenticationManager(AuthenticationProvider authenticationProvider) throws Exception {
        return new ProviderManager(authenticationProvider);
    }
    // Configure the password encoder to use Argon2, which is a secure hashing algorithm for passwords.
    // The encrytion parameters is the same as the AuthenticationImpl(generate password when creating user) class,
    // it ensures that the password hashing and verification processes are consistent across the application.
    @Bean
    PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(64, 128, 2, 1 << 16, 4);
    }
}
