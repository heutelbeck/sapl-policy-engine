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
package io.sapl.node.http;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import io.sapl.node.SaplNodeProperties;
import io.sapl.node.auth.UserLookupService;
import io.sapl.node.http.auth.CachingHttpAuthHandler;
import io.sapl.node.http.auth.HttpAuthHandler;
import io.sapl.node.http.servlet.DecideOnceServlet;
import io.sapl.node.http.servlet.DecideStreamServlet;
import io.sapl.node.http.servlet.MultiDecideAllOnceServlet;
import io.sapl.node.http.servlet.MultiDecideAllServlet;
import io.sapl.node.http.servlet.MultiDecideServlet;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the bypass-Spring HTTP PDP endpoint. Registers raw servlets for the
 * {@code /api/pdp/*} routes via {@link ServletRegistrationBean} so that
 * Jetty dispatches them directly, skipping Spring MVC's
 * {@code DispatcherServlet}. Spring Security is configured to ignore the
 * same routes; per-request authentication is handled by
 * {@link HttpAuthHandler} which caches verified credentials.
 */
@Configuration
@Profile("!cli")
class PdpHttpEndpointConfiguration {

    @Bean
    HttpAuthHandler httpAuthHandler(SaplNodeProperties properties, UserLookupService userLookupService,
            PasswordEncoder passwordEncoder, @Nullable JwtDecoder jwtDecoder,
            @Value("${io.sapl.node.http.auth-cache.positive-ttl:5m}") Duration positiveTtl,
            @Value("${io.sapl.node.http.auth-cache.negative-ttl:5s}") Duration negativeTtl,
            @Value("${io.sapl.node.http.auth-cache.max-size:10000}") long maxSize) {
        return new CachingHttpAuthHandler(properties, userLookupService, passwordEncoder, jwtDecoder, positiveTtl,
                negativeTtl, maxSize);
    }

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService sseKeepAliveScheduler(
            @Value("${io.sapl.node.http.sse.keep-alive-pool-size:0}") int configuredPoolSize) {
        val poolSize    = configuredPoolSize > 0 ? configuredPoolSize
                : Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        val threadIndex = new AtomicInteger();
        return Executors.newScheduledThreadPool(poolSize, r -> {
            val t = new Thread(r, "sapl-sse-keepalive-" + threadIndex.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService sseStreamPumpExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    ServletRegistrationBean<DecideOnceServlet> decideOnceServletRegistration(BlockingPolicyDecisionPoint pdp,
            HttpAuthHandler authHandler, JsonMapper mapper) {
        val servlet      = new DecideOnceServlet(pdp, authHandler, mapper);
        val registration = new ServletRegistrationBean<>(servlet, "/api/pdp/decide-once");
        registration.setName("saplDecideOnceServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean
    ServletRegistrationBean<MultiDecideAllOnceServlet> multiDecideAllOnceServletRegistration(
            BlockingPolicyDecisionPoint pdp, HttpAuthHandler authHandler, JsonMapper mapper) {
        val servlet      = new MultiDecideAllOnceServlet(pdp, authHandler, mapper);
        val registration = new ServletRegistrationBean<>(servlet, "/api/pdp/multi-decide-all-once");
        registration.setName("saplMultiDecideAllOnceServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean
    ServletRegistrationBean<DecideStreamServlet> decideStreamServletRegistration(BlockingPolicyDecisionPoint pdp,
            HttpAuthHandler authHandler, JsonMapper mapper, ScheduledExecutorService sseKeepAliveScheduler,
            ExecutorService sseStreamPumpExecutor,
            @Value("#{'${io.sapl.server.keep-alive:${io.sapl.node.keep-alive:0}}'}") long keepAliveSeconds) {
        val servlet      = new DecideStreamServlet(pdp, authHandler, mapper, Duration.ofSeconds(keepAliveSeconds),
                sseKeepAliveScheduler, sseStreamPumpExecutor);
        val registration = new ServletRegistrationBean<>(servlet, "/api/pdp/decide");
        registration.setName("saplDecideStreamServlet");
        registration.setLoadOnStartup(1);
        registration.setAsyncSupported(true);
        return registration;
    }

    @Bean
    ServletRegistrationBean<MultiDecideServlet> multiDecideServletRegistration(BlockingPolicyDecisionPoint pdp,
            HttpAuthHandler authHandler, JsonMapper mapper, ScheduledExecutorService sseKeepAliveScheduler,
            ExecutorService sseStreamPumpExecutor,
            @Value("#{'${io.sapl.server.keep-alive:${io.sapl.node.keep-alive:0}}'}") long keepAliveSeconds) {
        val servlet      = new MultiDecideServlet(pdp, authHandler, mapper, Duration.ofSeconds(keepAliveSeconds),
                sseKeepAliveScheduler, sseStreamPumpExecutor);
        val registration = new ServletRegistrationBean<>(servlet, "/api/pdp/multi-decide");
        registration.setName("saplMultiDecideServlet");
        registration.setLoadOnStartup(1);
        registration.setAsyncSupported(true);
        return registration;
    }

    @Bean
    ServletRegistrationBean<MultiDecideAllServlet> multiDecideAllServletRegistration(BlockingPolicyDecisionPoint pdp,
            HttpAuthHandler authHandler, JsonMapper mapper, ScheduledExecutorService sseKeepAliveScheduler,
            ExecutorService sseStreamPumpExecutor,
            @Value("#{'${io.sapl.server.keep-alive:${io.sapl.node.keep-alive:0}}'}") long keepAliveSeconds) {
        val servlet      = new MultiDecideAllServlet(pdp, authHandler, mapper, Duration.ofSeconds(keepAliveSeconds),
                sseKeepAliveScheduler, sseStreamPumpExecutor);
        val registration = new ServletRegistrationBean<>(servlet, "/api/pdp/multi-decide-all");
        registration.setName("saplMultiDecideAllServlet");
        registration.setLoadOnStartup(1);
        registration.setAsyncSupported(true);
        return registration;
    }
}
