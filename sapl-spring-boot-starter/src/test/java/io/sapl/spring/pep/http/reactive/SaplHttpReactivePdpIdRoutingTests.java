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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.config.Customizer.withDefaults;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.spring.config.PdpIdAuthenticationExtractor;
import io.sapl.spring.testsupport.SaplPepTestApp;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Multi-tenant routing: the per-request PDP id extracted from the
 * authentication
 * must reach the reactive authorization manager. The PdpIdWebFilter has to run
 * after authentication but before authorization; if it does not, the manager
 * silently falls back to the default PDP id for every tenant.
 */
@SpringBootTest(classes = SaplHttpReactivePdpIdRoutingTests.TestApp.class, properties = "spring.main.web-application-type=reactive")
class SaplHttpReactivePdpIdRoutingTests {

    @Autowired
    ApplicationContext context;

    @MockitoBean
    ReactivePolicyDecisionPoint pdp;

    @Test
    @DisplayName("the authenticated tenant's PDP id reaches the authorization manager, not the default")
    void givenAuthenticatedTenantThenManagerReceivesTenantPdpId() {
        when(pdp.decide(any(AuthorizationSubscription.class), anyString()))
                .thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        val client = WebTestClient.bindToApplicationContext(context)
                .apply(SecurityMockServerConfigurers.springSecurity()).configureClient().build();

        client.mutateWith(SecurityMockServerConfigurers.mockUser("tarsis")).get().uri("/hello").exchange()
                .expectStatus().isOk();

        val pdpId = ArgumentCaptor.forClass(String.class);
        verify(pdp).decide(any(AuthorizationSubscription.class), pdpId.capture());
        assertThat(pdpId.getValue()).isEqualTo("pdp-tarsis").isNotEqualTo(ReactivePolicyDecisionPoint.DEFAULT_PDP_ID);
    }

    @SaplPepTestApp
    @EnableWebFluxSecurity
    static class TestApp {

        @Bean
        SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ApplicationContext ctx) {
            SaplServerHttpSecurityConfigurer.apply(http, ctx);
            return http.csrf(ServerHttpSecurity.CsrfSpec::disable).httpBasic(withDefaults()).build();
        }

        @Bean
        PdpIdAuthenticationExtractor pdpIdAuthenticationExtractor() {
            return authentication -> Mono.just("pdp-" + authentication.getName());
        }

        @Bean
        MapReactiveUserDetailsService userDetailsService() {
            @SuppressWarnings("deprecation")
            val user = User.withDefaultPasswordEncoder().username("tarsis").password("x").roles("USER").build();
            return new MapReactiveUserDetailsService(user);
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
