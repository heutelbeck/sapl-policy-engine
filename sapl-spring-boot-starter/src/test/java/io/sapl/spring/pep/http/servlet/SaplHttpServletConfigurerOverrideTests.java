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
package io.sapl.spring.pep.http.servlet;

import static io.sapl.spring.pep.http.servlet.SaplHttpSecurityConfigurer.saplHttp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates that the configurer-level {@code subscriptionFactory(...)} hook
 * actually shapes the subscription that hits the PDP in a fully wired
 * filter chain. The default factory bean is registered by the auto-config
 * but must be bypassed when the configurer carries a custom factory.
 */
@SpringBootTest(classes = SaplHttpServletConfigurerOverrideTests.TestApp.class)
@AutoConfigureMockMvc
class SaplHttpServletConfigurerOverrideTests {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PolicyDecisionPoint pdp;

    @Test
    @DisplayName("Configurer subscriptionFactory(...) shapes the subscription that reaches the PDP")
    @WithMockUser(username = "alice")
    void configurerOverridePropagatesToPdp() throws Exception {
        when(pdp.decideOnceBlocking(any())).thenReturn(AuthorizationDecision.PERMIT);

        mockMvc.perform(get("/hello")).andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(AuthorizationSubscription.class);
        verify(pdp, atLeastOnce()).decideOnceBlocking(captor.capture());

        assertThat(captor.getAllValues()).allSatisfy(captured -> {
            assertThat(captured.subject()).isEqualTo(Value.of("alice"));
            assertThat(captured.action()).isEqualTo(Value.of("GET"));
            assertThat(captured.resource()).isEqualTo(Value.of("/hello"));
        });
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableWebSecurity
    static class TestApp {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
            return http
                    .with(saplHttp(),
                            c -> c.subscriptionFactory((auth, req) -> AuthorizationSubscription.of(auth.getName(),
                                    req.getMethod(), req.getRequestURI(), mapper)))
                    .csrf(AbstractHttpConfigurer::disable).httpBasic(withDefaults()).build();
        }

        @Bean
        MockMvcBuilderCustomizer saplSecurityMockMvcCustomizer() {
            return builder -> builder.apply(SecurityMockMvcConfigurers.springSecurity());
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
