/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.attributeapi.auth;

import io.sapl.attributeapi.auth.ApiKey.ApiKeyAuthenticationFilter;
import io.sapl.attributeapi.auth.ApiKey.ApiKeyAuthenticationProvider;
import io.sapl.attributeapi.auth.ApiKey.ApiKeyAuthenticationService;
import io.sapl.attributeapi.auth.OAuth2.PdpIdJwtAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.http.SessionCreationPolicy;
import static org.springframework.security.config.Customizer.withDefaults;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AttributeApiSecurityProperties.class)
@ConditionalOnProperty(name = "io.sapl.attribute-api.enabled", havingValue = "true")
@RequiredArgsConstructor

public class AttributeSecurityConfiguration {
    private final AttributeApiSecurityProperties properties;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String jwtIssuerUri;

    @Bean
    @Order(1)
    public SecurityFilterChain attributeApiSecurityFilterChain(HttpSecurity http) throws Exception {
        // Scoped to this module's endpoints only, so it can coexist with a
        // host application's own catch-all SecurityFilterChain (e.g. when
        // embedded inside sapl-node).
        http.securityMatcher("/api/attributes/**");

        // CSRF is not needed because we have a stateless API
        http.csrf(AbstractHttpConfigurer::disable);
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (noAuthenticationMechanismIsDefined()) {
            throw new IllegalStateException("No authentication method set");
        }

        if (properties.isAllowNoAuth() && !properties.isAllowBasicAuth() && !properties.isAllowApiKeyAuth()
                && !properties.isAllowOAuth2Auth()) {
            log.warn("Server has been configured to reply to requests without authentication.");
            return http.httpBasic(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }

        var authenticationEntryPoint = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        http.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint));

        if (properties.isAllowBasicAuth()) {
            log.info("Basic authentication activated.");
            if (!hasBasicAuthUsers()) {
                log.warn("Basic authentication is enabled but no users with basic credentials are configured.");
            }
            http.httpBasic(withDefaults());
        } else {
            http.httpBasic(AbstractHttpConfigurer::disable);
        }

        if (properties.isAllowApiKeyAuth()) {
            log.info("API key authentication activated.");

            if (!hasApiKeyUsers()) {
                log.warn("API key authentication is enabled but no api key is defined.");
            }

            AuthenticationManager apiKeyAuthenticationManager = new ProviderManager(
                    new ApiKeyAuthenticationProvider(new ApiKeyAuthenticationService(properties)));
            http.addFilterBefore(new ApiKeyAuthenticationFilter(apiKeyAuthenticationManager, authenticationEntryPoint),
                    UsernamePasswordAuthenticationFilter.class);
        }

        if (properties.isAllowOAuth2Auth()) {
            log.info("OAuth2 authentication activated");

            var converter = new PdpIdJwtAuthenticationConverter(properties.getOauth2().getOidcPdpIdClaim());
            var decoder   = jwtDecoder();

            // API keys are also carried as "Authorization: Bearer sapl_..." - leave
            // those alone here so the apiKeyFilter above gets a chance to handle
            // them instead of failing JWT decoding.
            http.oauth2ResourceServer(oauth2 -> oauth2.bearerTokenResolver(request -> {
                String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
                if (authHeader != null && authHeader.startsWith("Bearer sapl_")) {
                    return null;
                }
                return new DefaultBearerTokenResolver().resolve(request);
            }).jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)));
        }

        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new AttributeApiUserDetailsService(properties);
    }

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    private boolean noAuthenticationMechanismIsDefined() {
        return !properties.isAllowNoAuth() && !properties.isAllowBasicAuth() && !properties.isAllowApiKeyAuth()
                && !properties.isAllowOAuth2Auth();
    }

    private boolean hasBasicAuthUsers() {
        return properties.getUsers().stream().anyMatch(user -> user.getBasic() != null);
    }

    private boolean hasApiKeyUsers() {
        return properties.getUsers().stream().anyMatch(user -> user.getKey() != null);
    }

    private JwtDecoder jwtDecoder() {
        if (jwtIssuerUri == null || jwtIssuerUri.isBlank()) {
            throw new IllegalStateException(
                    "OAuth2 authentication is enabled but 'spring.security.oauth2.resourceserver.jwt.issuer-uri' is not set.");
        }
        return JwtDecoders.fromIssuerLocation(jwtIssuerUri);
    }
}
