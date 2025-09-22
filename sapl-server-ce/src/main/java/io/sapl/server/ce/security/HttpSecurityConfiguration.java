/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.security;

import com.vaadin.flow.spring.security.VaadinAwareSecurityContextHolderStrategyConfiguration;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import io.sapl.server.ce.security.apikey.ApiKeyHeaderAuthFilterService;
import io.sapl.server.ce.security.apikey.ApiKeyService;
import io.sapl.server.ce.ui.views.login.LoginView;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.HttpStatusAccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.util.*;

/**
 * Security configuration for SAPL Server CE.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Conditional(SetupFinishedCondition.class)
@Import(VaadinAwareSecurityContextHolderStrategyConfiguration.class)
public class HttpSecurityConfiguration {

    @Value("${io.sapl.server.allowBasicAuth:#{false}}")
    private boolean allowBasicAuth;

    @Value("${io.sapl.server.allowApiKeyAuth:#{true}}")
    private boolean allowApiKeyAuth;

    @Value("${io.sapl.server.allowOauth2Auth:#{false}}")
    private boolean allowOauth2Auth;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:#{null}}")
    private String jwtIssuerURI;

    @Value("${io.sapl.server.allowOAuth2Login:#{false}}")
    private boolean allowOAuth2Login;

    private final ApiKeyHeaderAuthFilterService apiKeyAuthenticationFilterService;

    private static final String GROUPS             = "groups";
    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM        = "roles";

    /**
     * Conditionally decodes JWTs based on issuer metadata when the issuer property
     * is set.
     * <p>
     * Property: {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}
     *
     * @return a configured JwtDecoder.
     */
    @Bean
    @ConditionalOnProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")
    JwtDecoder jwtDecoder() {
        log.info("Initializing JwtDecoder from issuer: {}", jwtIssuerURI);
        return JwtDecoders.fromIssuerLocation(jwtIssuerURI);
    }

    /**
     * Converts a validated JWT into a single client authority used for API
     * authorization.
     *
     * @return the JwtAuthenticationConverter assigning
     * {@code ClientDetailsService.CLIENT}.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        val converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> List.of(new SimpleGrantedAuthority(Roles.CLIENT)));
        return converter;
    }

    /**
     * API filter chain for {@code /api/**}.
     * <p>
     * Stateless, CSRF disabled, strict 403 on unauthenticated/denied even when
     * Basic is enabled.
     * API key auth filter is integrated; OAuth2 Resource Server (JWT) is optional.
     * All API requests require {@code ClientDetailsService.CLIENT} authority.
     *
     * @param http the HttpSecurity to configure.
     * @return configured SecurityFilterChain for API endpoints.
     * @throws Exception if configuration fails.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain apiAuthnFilterChain(HttpSecurity http) throws Exception {
        val forbidden = new HttpStatusEntryPoint(HttpStatus.FORBIDDEN);

        log.info("Configuring API chain for '/api/**': stateless={}, apiKey={}, basicAuth={}, oauth2ResourceServer={}",
                true, allowApiKeyAuth, allowBasicAuth, allowOauth2Auth);

        http.securityMatcher("/api/**").requestCache(RequestCacheConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).oauth2Login(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable).csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(forbidden)
                        .accessDeniedHandler(new HttpStatusAccessDeniedHandler(HttpStatus.FORBIDDEN)));

        if (allowApiKeyAuth) {
            log.info("Enabling API key authentication for API chain.");
            http.addFilterBefore(apiKeyAuthenticationFilterService, UsernamePasswordAuthenticationFilter.class);
        }

        if (allowOauth2Auth) {
            log.info("Enabling OAuth2 Resource Server (JWT) for API chain with issuer '{}'.", jwtIssuerURI);
            http.oauth2ResourceServer(
                    oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                            .bearerTokenResolver(new BearerTokenResolver() {
                                final BearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();

                                @Override
                                public String resolve(HttpServletRequest request) {
                                    // API key takes precedence; suppress JWT if API key token is present.
                                    return ApiKeyService.getApiKeyToken(request) != null ? null
                                            : defaultResolver.resolve(request);
                                }
                            }).jwt(Customizer.withDefaults()));
        }

        if (allowBasicAuth) {
            log.info("Enabling HTTP Basic authentication with strict 403 entry point for API chain.");
            // Enforce 403 even with Basic enabled; prevents 401 challenge headers.
            http.httpBasic(basic -> basic.authenticationEntryPoint(forbidden));
        } else {
            log.info("HTTP Basic authentication is disabled for API chain.");
        }

        // *** Single 'anyRequest' definition to avoid "Can't configure anyRequest after
        // itself" ***
        http.authorizeHttpRequests(authz -> authz.anyRequest().hasAuthority(Roles.CLIENT));

        return http.build();
    }

    /**
     * UI filter chain for Vaadin Flow application and remaining endpoints.
     * <p>
     * Permits {@code /images/*.png}, ignores CSRF for {@code /xtext-service/**},
     * applies Vaadin integration, and configures form login or OAuth2 login.
     *
     * @param http the HttpSecurity to configure.
     * @param authoritiesMapper the mapper applied explicitly to the OAuth2 login
     * user info endpoint.
     * @return configured SecurityFilterChain for UI.
     * @throws Exception if configuration fails.
     */
    @Bean
    SecurityFilterChain uiFilterChain(HttpSecurity http, GrantedAuthoritiesMapper authoritiesMapper) throws Exception {
        val mvc = PathPatternRequestMatcher.withDefaults();

        log.info("Configuring UI chain: oauth2LoginEnabled={}", allowOAuth2Login);

        // Apply Vaadin integration first so it can register its request matchers before
        // anyRequest().
        http.with(VaadinSecurityConfigurer.vaadin(), vaadin -> {
            if (!allowOAuth2Login) {
                vaadin.loginView(LoginView.class);
            }
        });

        // Permit public images and ignore CSRF for Xtext services; remaining
        // authorization rules are
        // handled by Vaadin's integration and the login configuration below.
        http.authorizeHttpRequests(authz -> authz.requestMatchers(mvc.matcher("/images/*.png")).permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers(mvc.matcher("/xtext-service/**")));

        if (allowOAuth2Login) {
            log.info("Enabling OAuth2 Login for UI chain with explicit authorities mapper.");
            http.oauth2Login(
                    oauth2 -> oauth2.userInfoEndpoint(userInfo -> userInfo.userAuthoritiesMapper(authoritiesMapper)))
                    .logout(logout -> logout.logoutUrl("/logout").invalidateHttpSession(true)
                            .deleteCookies("JSESSIONID").logoutSuccessUrl("/oauth2"))
                    .authorizeHttpRequests(authz -> authz
                            .requestMatchers("/unauthenticated", "/oauth2/**", "/login/**", "/VAADIN/push/**")
                            .permitAll());
        } else {
            log.info("Enabling form login for UI chain; login view is set via Vaadin configurer.");
        }

        return http.build();
    }

    /**
     * Maps OIDC/OAuth2 user claims into Spring Security ROLE_* authorities.
     * Extracts roles from Keycloak-compatible {@code realm_access.roles} or from
     * {@code groups}.
     */
    @Bean
    GrantedAuthoritiesMapper userAuthoritiesMapperForKeycloak2() {
        return authorities -> {
            Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
            GrantedAuthority      authority         = authorities.iterator().next();
            boolean               isOidc            = authority instanceof OidcUserAuthority;

            if (isOidc) {
                val oidcUserAuthority = (OidcUserAuthority) authority;
                val userInfo          = oidcUserAuthority.getUserInfo();

                if (userInfo.hasClaim(REALM_ACCESS_CLAIM)) {
                    Map<String, Object> realmAccess = userInfo.getClaimAsMap(REALM_ACCESS_CLAIM);
                    Collection<?>       rawRoles    = (Collection<?>) realmAccess.get(ROLES_CLAIM);
                    Collection<String>  roles       = rawRoles.stream().filter(String.class::isInstance)
                            .map(String.class::cast).toList();
                    mappedAuthorities.addAll(generateAuthoritiesFromClaim(roles));
                } else if (userInfo.hasClaim(GROUPS)) {
                    Collection<String> roles = userInfo.getClaimAsStringList(GROUPS);
                    mappedAuthorities.addAll(generateAuthoritiesFromClaim(roles));
                }
            } else {
                val    oAuth2UserAuthority = (OAuth2UserAuthority) authority;
                val    userAttributes      = oAuth2UserAuthority.getAttributes();
                val    realmAccess         = convertAttributeToMapIfPossible(userAttributes.get(REALM_ACCESS_CLAIM));
                Object rawRoles            = realmAccess.get(ROLES_CLAIM);
                if (rawRoles instanceof Collection<?> rawRolesCollection) {
                    Collection<String> roles = rawRolesCollection.stream().filter(String.class::isInstance)
                            .map(String.class::cast).toList();
                    mappedAuthorities.addAll(generateAuthoritiesFromClaim(roles));
                }
            }
            return mappedAuthorities;
        };
    }

    private Map<String, Object> convertAttributeToMapIfPossible(Object suspectedMap) {
        final var newMap = new HashMap<String, Object>();
        if (suspectedMap instanceof Map<?, ?> claimsMap) {
            for (var entry : claimsMap.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    newMap.put(key, entry.getValue());
                }
            }
        }
        return newMap;
    }

    Collection<SimpleGrantedAuthority> generateAuthoritiesFromClaim(Collection<String> roles) {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
    }
}
