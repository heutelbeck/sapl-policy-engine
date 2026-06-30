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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.DisposableBean;

import lombok.val;

/**
 * Owns the executors used by the Server-Sent-Events PDP endpoints.
 * <p>
 * The executors are plain fields, never Spring beans, and they are shut down
 * through {@link DisposableBean#destroy()}, which the container calls through the
 * interface rather than by reflection. Exposing the executors as
 * {@link AutoCloseable} beans would make the container infer a reflective
 * shutdown at context destroy, which a native image cannot perform, because
 * Spring AOT does not preserve {@code @Bean(destroyMethod = "")}.
 */
final class SseExecutors implements DisposableBean {

    private final ScheduledExecutorService keepAliveScheduler;
    private final ExecutorService          streamPumpExecutor;

    SseExecutors(int keepAlivePoolSize) {
        this.keepAliveScheduler = createKeepAliveScheduler(keepAlivePoolSize);
        this.streamPumpExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    ScheduledExecutorService keepAliveScheduler() {
        return keepAliveScheduler;
    }

    ExecutorService streamPumpExecutor() {
        return streamPumpExecutor;
    }

    private static ScheduledExecutorService createKeepAliveScheduler(int configuredPoolSize) {
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

    @Override
    public void destroy() {
        // shutdownNow (not shutdown) so pumps blocked in awaitNext() are interrupted at
        // context destroy. Without this, long-lived SSE streams hold the JVM open past
        // the normal shutdown window.
        keepAliveScheduler.shutdown();
        streamPumpExecutor.shutdownNow();
    }
}
