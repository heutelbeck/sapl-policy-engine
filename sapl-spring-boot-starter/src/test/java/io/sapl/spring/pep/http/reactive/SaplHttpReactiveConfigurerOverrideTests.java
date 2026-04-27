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
package io.sapl.spring.pep.http.reactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.config.Customizer.withDefaults;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates that the reactive configurer's
 * {@code subscriptionFactory(...)} hook actually shapes the subscription
 * that hits the PDP in a fully wired filter chain. Mirror of the servlet
 * override test.
 */
@SpringBootTest(classes = SaplHttpReactiveConfigurerOverrideTests.TestApp.class, properties = "spring.main.web-application-type=reactive")
class SaplHttpReactiveConfigurerOverrideTests {

    @Autowired
    ApplicationContext context;

    @MockitoBean
    PolicyDecisionPoint pdp;

    @Test
    @DisplayName("Reactive configurer subscriptionFactory(...) shapes the subscription that reaches the PDP")
    @WithMockUser(username = "alice")
    void configurerOverridePropagatesToPdp() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

        val client = WebTestClient.bindToApplicationContext(context).apply(
                org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity())
                .configureClient().build();

        client.get().uri("/hello").exchange().expectStatus().isOk();

        val captor = ArgumentCaptor.forClass(AuthorizationSubscription.class);
        verify(pdp).decide(captor.<AuthorizationSubscription>capture());

        val captured = captor.getValue();
        assertThat(captured.subject()).isEqualTo(Value.of("alice"));
        assertThat(captured.action()).isEqualTo(Value.of("GET"));
        assertThat(captured.resource()).isEqualTo(Value.of("/hello"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableWebFluxSecurity
    static class TestApp {

        @Bean
        SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ApplicationContext ctx,
                ObjectMapper mapper) {
            SaplServerHttpSecurityConfigurer.apply(http, ctx,
                    c -> c.subscriptionFactory((auth,
                            exchange) -> Mono.just(AuthorizationSubscription.of(auth.getName(),
                                    exchange.getRequest().getMethod().name(), exchange.getRequest().getURI().getPath(),
                                    mapper))));
            return http.csrf(ServerHttpSecurity.CsrfSpec::disable).httpBasic(withDefaults()).build();
        }

        @Bean
        org.springframework.security.core.userdetails.MapReactiveUserDetailsService userDetailsService() {
            @SuppressWarnings("deprecation")
            org.springframework.security.core.userdetails.UserDetails alice = org.springframework.security.core.userdetails.User
                    .withDefaultPasswordEncoder().username("alice").password("pw").roles("USER").build();
            return new org.springframework.security.core.userdetails.MapReactiveUserDetailsService(alice);
        }

        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    static class TestController {
        @GetMapping("/hello")
        String hello() {
            return "hello";
        }
    }
}
