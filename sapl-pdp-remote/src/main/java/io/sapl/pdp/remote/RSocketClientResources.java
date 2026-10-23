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
package io.sapl.pdp.remote;

import static lombok.AccessLevel.PRIVATE;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.jspecify.annotations.Nullable;

import lombok.RequiredArgsConstructor;
import reactor.netty.resources.LoopResources;
import reactor.netty.tcp.TcpClient;

/**
 * Shared RSocket client event loops, released when the last client closes.
 */
@RequiredArgsConstructor(access = PRIVATE)
public final class RSocketClientResources implements AutoCloseable {

    private static final String ERROR_RESOURCES_CLOSED = "RSocket client resources are closed.";

    private static final ReentrantLock LIFECYCLE_LOCK = new ReentrantLock();

    private static @Nullable LoopResources sharedLoops;
    private static int                     users;

    private final LoopResources loops;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Acquires the shared RSocket client event loops.
     *
     * @return a handle that must be closed when the client is disposed
     */
    public static RSocketClientResources acquire() {
        LIFECYCLE_LOCK.lock();
        try {
            if (sharedLoops == null) {
                // Transport compatibility checks consume an extra client loop selection.
                sharedLoops = LoopResources.create("sapl-rsocket-client", 2 * LoopResources.DEFAULT_IO_WORKER_COUNT,
                        true);
            }
            users++;
            return new RSocketClientResources(sharedLoops);
        } finally {
            LIFECYCLE_LOCK.unlock();
        }
    }

    /**
     * Configures a TCP client to use the shared RSocket client event loops.
     *
     * @param client the TCP client, including its address and TLS configuration
     * @return the TCP client with dedicated RSocket event loops
     * @throws IllegalStateException if this handle is already closed
     */
    public TcpClient configure(TcpClient client) {
        if (closed.get()) {
            throw new IllegalStateException(ERROR_RESOURCES_CLOSED);
        }
        return client.runOn(loops);
    }

    /**
     * Releases this handle and shuts down the loops after the last handle closes.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            release();
        }
    }

    private static void release() {
        LIFECYCLE_LOCK.lock();
        try {
            if (--users == 0 && sharedLoops != null) {
                sharedLoops.dispose();
                sharedLoops = null;
            }
        } finally {
            LIFECYCLE_LOCK.unlock();
        }
    }
}
