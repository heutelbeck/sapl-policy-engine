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
package io.sapl.server.lt;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Role;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity;
import org.springframework.security.config.annotation.rsocket.PayloadInterceptorOrder;
import org.springframework.security.config.annotation.rsocket.RSocketSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.FormLoginSpec;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.messaging.handler.invocation.reactive.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.rsocket.authentication.AuthenticationPayloadExchangeConverter;
import org.springframework.security.rsocket.authentication.AuthenticationPayloadInterceptor;
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.web.server.ServerWebExchange;

import io.sapl.server.lt.apikey.ApiKeyReactiveAuthenticationManager;
import io.sapl.server.lt.apikey.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
@EnableRSocketSecurity
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final ApiKeyService          apiKeyService;
    private final SAPLServerLTProperties pdpProperties;
    private final PasswordEncoder        passwordEncoder;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:#{null}}")
    private String jwtIssuerURI;

    @Bean
    SecurityWebFilterChain securityFilterChainLocal(ServerHttpSecurity http) {
        http = http.csrf(CsrfSpec::disable);

        if (noAuthenticationMechanismIsDefined()) {
            throw new IllegalStateException(
                    "No authentication mechanism for clients defined. Set up your local/container configuration. If the server should respond to unauthenticated requests, this has to be explicitly activated.");
        }

        if (pdpProperties.isAllowNoAuth()) {
            log.warn("Server has been configured to reply to requests without authentication.");
            http = http.authorizeExchange(exchange -> exchange.pathMatchers("/**").permitAll());
        } else {
            // any other request requires the user to be authenticated
            http = http.authorizeExchange(exchange -> exchange.anyExchange().authenticated());
        }

        if (pdpProperties.isAllowApiKeyAuth()) {
            log.info("API key authentication activated.");
            if (pdpProperties.getAllowedApiKeys().isEmpty()) {
                log.warn(
                        "No API keys for clients defined. Please set: 'io.sapl.server-lt.key.allowedApiKeys'. With a list of valid keys.");
            }
            final var customAuthenticationWebFilter = new AuthenticationWebFilter(
                    new ApiKeyReactiveAuthenticationManager());
            customAuthenticationWebFilter
                    .setServerAuthenticationConverter(apiKeyService.getHttpApiKeyAuthenticationConverter());
            http = http.addFilterAt(customAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION);
        }

        if (pdpProperties.isAllowBasicAuth()) {
            log.info("Basic authentication activated.");
            http = http.httpBasic(withDefaults());
        }

        if (pdpProperties.isAllowOauth2Auth()) {
            log.info("OAuth2 authentication activated. Accepting JWT tokens from issuer: {}", jwtIssuerURI);
            if (jwtIssuerURI == null) {
                throw new IllegalStateException(
                        "If JWT authentication is active, a token issuer must be supplied. Please set: 'spring.security.oauth2.resourceserver.jwt.issuer-uri'.");
            }
            http = http.oauth2ResourceServer(
                    oauth2 -> oauth2.bearerTokenConverter(new ServerBearerTokenAuthenticationConverter() {
                        @Override
                        public Mono<Authentication> convert(ServerWebExchange exchange) {
                            if (ApiKeyService.getApiKeyToken(exchange).isPresent()) {
                                // This Bearer token is used for sapl api key authentication
                                return Mono.empty();
                            } else {
                                return super.convert(exchange);
                            }
                        }
                    }).jwt(Customizer.withDefaults()));
        }

        return http.formLogin(FormLoginSpec::disable).build();
    }

    private boolean noAuthenticationMechanismIsDefined() {
        return !pdpProperties.isAllowNoAuth() && !pdpProperties.isAllowBasicAuth() && !pdpProperties.isAllowApiKeyAuth()
                && !pdpProperties.isAllowOauth2Auth();
    }

    @Bean
    MapReactiveUserDetailsService userDetailsServiceLocal() {
        if (!pdpProperties.isAllowBasicAuth()) {
            return null;
        }
        final var key = pdpProperties.getKey();
        if (key == null) {
            throw new IllegalStateException(
                    "If Basic authentication is active, a client key must be supplied. Please set: 'io.sapl.server-lt.key'.");
        }
        final var secret = pdpProperties.getSecret();
        if (secret == null) {
            throw new IllegalStateException(
                    "If Basic authentication is active, a client secret must be supplied. Please set: 'io.sapl.server-lt.secret'. As a BCrypt encoded secret.");
        }
        final var client = User.builder().username(key).password(secret).roles("PDP_CLIENT").build();
        return new MapReactiveUserDetailsService(client);
    }

    /**
     * The RSocketMessageHandler Bean (rsocketMessageHandler), activates parts of
     * the Spring Security component model that let us inject the authenticated user
     * into our handler methods (those annotated with @MessageMapping).
     */
    @Bean
    RSocketMessageHandler rsocketMessageHandler(RSocketStrategies rSocketStrategies) {
        final var rSocketMessageHandler = new RSocketMessageHandler();
        rSocketMessageHandler.getArgumentResolverConfigurer()
                .addCustomResolver(new AuthenticationPrincipalArgumentResolver());
        rSocketMessageHandler.setRSocketStrategies(rSocketStrategies);
        return rSocketMessageHandler;
    }

    /**
     * Decodes JSON Web Token (JWT) according to the configuration that was
     * initialized by the OpenID Provider specified in the jwtIssuerURI.
     */
    @Bean
    @ConditionalOnProperty(prefix = "io.sapl.server-lt", name = "allowOauth2Auth", havingValue = "true")
    ReactiveJwtDecoder jwtDecoder() {
        if (pdpProperties.isAllowOauth2Auth()) {
            return ReactiveJwtDecoders.fromIssuerLocation(jwtIssuerURI);
        } else {
            return null;
        }
    }

    /**
     * The PayloadSocketAcceptorInterceptor Bean (rsocketPayloadAuthorization)
     * configures the Security Filter Chain for RSocket payloads. Supported
     * Authentication Methods are: NoAuth, BasicAuth, Oauth2 (JWT) and ApiKey.
     */
    @Bean
    PayloadSocketAcceptorInterceptor rsocketPayloadAuthorization(RSocketSecurity security) {
        security = security.authorizePayload(spec -> {
            if (pdpProperties.isAllowNoAuth()) {
                spec.anyExchange().permitAll();
            } else {
                spec.anyRequest().authenticated().anyExchange().permitAll();
            }
        });

        // Configure Basic and OAuth Authentication
        UserDetailsRepositoryReactiveAuthenticationManager simpleManager = null;
        if (pdpProperties.isAllowBasicAuth()) {
            simpleManager = new UserDetailsRepositoryReactiveAuthenticationManager(this.userDetailsServiceLocal());
            simpleManager.setPasswordEncoder(passwordEncoder);
        }

        JwtReactiveAuthenticationManager jwtManager = null;
        if (pdpProperties.isAllowOauth2Auth()) {
            jwtManager = new JwtReactiveAuthenticationManager(ReactiveJwtDecoders.fromIssuerLocation(jwtIssuerURI));
        }

        final var finalSimpleManager = simpleManager;
        final var finalJwtManager    = jwtManager;
        final var auth               = new AuthenticationPayloadInterceptor(authentication -> {
                                         if (finalSimpleManager != null
                                                 && authentication instanceof UsernamePasswordAuthenticationToken) {
                                             return finalSimpleManager.authenticate(authentication);
                                         } else if (finalJwtManager != null
                                                 && authentication instanceof BearerTokenAuthenticationToken) {
                                             return finalJwtManager.authenticate(authentication);
                                         } else {
                                             throw new IllegalArgumentException("Unsupported Authentication Type "
                                                     + authentication.getClass().getSimpleName());
                                         }
                                     });
        auth.setAuthenticationConverter(new AuthenticationPayloadExchangeConverter());
        auth.setOrder(PayloadInterceptorOrder.AUTHENTICATION.getOrder());
        security.addPayloadInterceptor(auth);

        // Configure ApiKey authentication
        if (pdpProperties.isAllowApiKeyAuth()) {
            final var manager           = new ApiKeyReactiveAuthenticationManager();
            final var apikeyInterceptor = new AuthenticationPayloadInterceptor(manager);
            apikeyInterceptor.setAuthenticationConverter(apiKeyService.getRsocketApiKeyAuthenticationConverter());
            apikeyInterceptor.setOrder(PayloadInterceptorOrder.AUTHENTICATION.getOrder());
            security.addPayloadInterceptor(apikeyInterceptor);
        }
        return security.build();

    }

    // Fixes issue present in spring boot 3.4.0:
    // https://github.com/spring-projects/spring-security/issues/16161#issuecomment-2498390492
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Primary
    static ObjectPostProcessor<ReactiveAuthorizationManager<ServerWebExchange>> primaryAuthorizationManagerPostProcessor() {
        return ObjectPostProcessor.identity();
    }

    @Bean
    @Role(2)
    @Primary
    static ObjectPostProcessor<ReactiveAuthenticationManager> primaryAuthenticationManagerPostProcessor() {
        return ObjectPostProcessor.identity();
    }

    @Bean
    @Role(2)
    @Primary
    static ObjectPostProcessor<WebFilterChainProxy.WebFilterChainDecorator> primaryFilterChainDecoratorPostProcessor() {
        return ObjectPostProcessor.identity();
    }
}
