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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import jakarta.servlet.Servlet;

import io.sapl.node.SaplNodeProperties;
import io.sapl.node.auth.UserLookupService;
import io.sapl.node.auth.http.CachingHttpAuthHandler;
import io.sapl.node.auth.http.HttpAuthHandler;
import io.sapl.node.http.pdp.DecideOnceServlet;
import io.sapl.node.http.pdp.DecideStreamServlet;
import io.sapl.node.http.pdp.MultiDecideAllOnceServlet;
import io.sapl.node.http.pdp.MultiDecideAllServlet;
import io.sapl.node.http.pdp.MultiDecideServlet;
import io.sapl.node.http.pdp.SseConnectionRegistry;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the bypass-Spring HTTP PDP endpoint. Registers raw servlets for the
 * {@code /api/pdp/*} routes via {@link ServletRegistrationBean} so that
 * Jetty dispatches them directly, skipping Spring MVC's
 * {@code DispatcherServlet}. Spring Security is configured to ignore the
 * same routes. Per-request authentication is handled by
 * {@link HttpAuthHandler} which caches verified credentials.
 */
@Configuration
class PdpHttpEndpointConfiguration {

    @Bean
    FilterRegistrationBean<RequestBodySizeLimitFilter> requestBodySizeLimitFilter(
            @Value("${io.sapl.node.http.max-request-body-bytes:65536}") long maxRequestBodyBytes) {
        // Global cap against oversized POST bodies on the SAPL HTTP surface.
        // 64 KiB default is generous for typical authorization subscriptions
        // (subject + action + resource + small context). Operators with very
        // large multi-decide payloads or rich environment maps can raise it.
        // This cap is HTTP only. The RSocket transport is bounded by its protocol
        // frame ceiling via sapl.pdp.rsocket.max-inbound-payload-size, which cannot
        // be set below 16 MiB.
        val registration = new FilterRegistrationBean<>(new RequestBodySizeLimitFilter(maxRequestBodyBytes));
        registration.addUrlPatterns("/api/pdp/*", "/access/v1/*");
        registration.setName("requestBodySizeLimitFilter");
        return registration;
    }

    @Bean
    HttpAuthHandler httpAuthHandler(SaplNodeProperties properties, UserLookupService userLookupService,
            @Nullable JwtDecoder jwtDecoder,
            @Value("${io.sapl.node.http.auth-cache.positive-ttl:5m}") Duration positiveTtl,
            @Value("${io.sapl.node.http.auth-cache.negative-ttl:5s}") Duration negativeTtl,
            @Value("${io.sapl.node.http.auth-cache.max-size:10000}") long maxSize) {
        return new CachingHttpAuthHandler(properties, userLookupService, jwtDecoder, positiveTtl, negativeTtl, maxSize);
    }

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService sseKeepAliveScheduler(
            @Value("${io.sapl.node.http.sse.keep-alive-pool-size:0}") int configuredPoolSize) {
        val poolSize    = configuredPoolSize > 0 ? configuredPoolSize
                : Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        val threadIndex = new AtomicInteger();
        val scheduler   = new ScheduledThreadPoolExecutor(poolSize, r -> {
                            val t = new Thread(r, "sapl-sse-keepalive-" + threadIndex.incrementAndGet());
                            t.setDaemon(true);
                            return t;
                        });
        // Purge cancelled keep-alive tasks promptly so they do not pile up under
        // connection churn.
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    // shutdownNow (not shutdown) so pumps blocked in awaitNext() are
    // interrupted at context destroy. Without this, long-lived SSE
    // streams hold the JVM open past Spring's normal shutdown window.
    @Bean(destroyMethod = "shutdownNow")
    ExecutorService sseStreamPumpExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    ServletRegistrationBean<DecideOnceServlet> decideOnceServletRegistration(BlockingPolicyDecisionPoint pdp,
            HttpAuthHandler authHandler, JsonMapper mapper) {
        return register(new DecideOnceServlet(pdp, authHandler, mapper), "/api/pdp/decide-once",
                "saplDecideOnceServlet", false);
    }

    @Bean
    ServletRegistrationBean<MultiDecideAllOnceServlet> multiDecideAllOnceServletRegistration(
            BlockingPolicyDecisionPoint pdp, HttpAuthHandler authHandler, JsonMapper mapper,
            @Value("${io.sapl.node.max-multi-subscription-count:256}") int maxMultiSubscriptionCount) {
        return register(new MultiDecideAllOnceServlet(pdp, authHandler, mapper, maxMultiSubscriptionCount),
                "/api/pdp/multi-decide-all-once", "saplMultiDecideAllOnceServlet", false);
    }

    @Bean
    ServletRegistrationBean<DecideStreamServlet> decideStreamServletRegistration(BlockingPolicyDecisionPoint pdp,
            HttpAuthHandler authHandler, JsonMapper mapper, ScheduledExecutorService sseKeepAliveScheduler,
            ExecutorService sseStreamPumpExecutor, SseConnectionRegistry sseConnectionRegistry,
            @Value("${io.sapl.node.keep-alive:15}") long keepAliveSeconds) {
        return register(
                new DecideStreamServlet(pdp, authHandler, mapper, Duration.ofSeconds(keepAliveSeconds),
                        sseKeepAliveScheduler, sseStreamPumpExecutor, sseConnectionRegistry),
                "/api/pdp/decide", "saplDecideStreamServlet", true);
    }

    @Bean
    ServletRegistrationBean<MultiDecideServlet> multiDecideServletRegistration(BlockingPolicyDecisionPoint pdp,
            HttpAuthHandler authHandler, JsonMapper mapper, ScheduledExecutorService sseKeepAliveScheduler,
            ExecutorService sseStreamPumpExecutor, SseConnectionRegistry sseConnectionRegistry,
            @Value("${io.sapl.node.keep-alive:15}") long keepAliveSeconds,
            @Value("${io.sapl.node.max-multi-subscription-count:256}") int maxMultiSubscriptionCount) {
        return register(
                new MultiDecideServlet(pdp, authHandler, mapper, Duration.ofSeconds(keepAliveSeconds),
                        sseKeepAliveScheduler, sseStreamPumpExecutor, sseConnectionRegistry, maxMultiSubscriptionCount),
                "/api/pdp/multi-decide", "saplMultiDecideServlet", true);
    }

    @Bean
    ServletRegistrationBean<MultiDecideAllServlet> multiDecideAllServletRegistration(BlockingPolicyDecisionPoint pdp,
            HttpAuthHandler authHandler, JsonMapper mapper, ScheduledExecutorService sseKeepAliveScheduler,
            ExecutorService sseStreamPumpExecutor, SseConnectionRegistry sseConnectionRegistry,
            @Value("${io.sapl.node.keep-alive:15}") long keepAliveSeconds,
            @Value("${io.sapl.node.max-multi-subscription-count:256}") int maxMultiSubscriptionCount) {
        return register(
                new MultiDecideAllServlet(pdp, authHandler, mapper, Duration.ofSeconds(keepAliveSeconds),
                        sseKeepAliveScheduler, sseStreamPumpExecutor, sseConnectionRegistry, maxMultiSubscriptionCount),
                "/api/pdp/multi-decide-all", "saplMultiDecideAllServlet", true);
    }

    private static <T extends Servlet> ServletRegistrationBean<T> register(T servlet, String urlPattern,
            String beanName, boolean asyncSupported) {
        val registration = new ServletRegistrationBean<>(servlet, urlPattern);
        registration.setName(beanName);
        registration.setLoadOnStartup(1);
        if (asyncSupported) {
            registration.setAsyncSupported(true);
        }
        return registration;
    }
}
