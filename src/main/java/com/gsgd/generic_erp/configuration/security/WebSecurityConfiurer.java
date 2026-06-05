package com.gsgd.generic_erp.configuration.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.gsgd.generic_erp.configuration.security.impl.CustomizedUserDetailServiceImpl;

@Configuration
@EnableWebSecurity
public class WebSecurityConfiurer {

    @Bean
    SecurityFilterChain filterChain(org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
        http.cors(cors->cors.configurationSource(null))
        .csrf(csrf->csrf.disable())
        .sessionManagement(sm->sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests((authz) -> authz
                .requestMatchers("/login","/logout")
                .permitAll()
                .anyRequest()
                .authenticated()
            );
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        return new CustomizedUserDetailServiceImpl();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(64, 128, 2, 1 << 16, 4);
    }
}
