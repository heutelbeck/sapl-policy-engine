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
package io.sapl.node.cli.benchmark;

import org.jspecify.annotations.Nullable;

/**
 * Context for loadtest report methodology section. Captures the protocol,
 * target, and configuration used for a remote server load test.
 *
 * @param protocol "HTTP" or "RSocket"
 * @param target the server address (URL for HTTP, host:port for RSocket)
 * @param concurrency number of concurrent requests
 * @param connections number of TCP connections (RSocket only)
 * @param vtPerConnection virtual threads per connection (RSocket only)
 * @param warmupSeconds warmup duration
 * @param measureSeconds measurement duration
 * @param timestamp run timestamp for filenames
 * @param label optional user-provided label for the report (null if not set)
 */
public record LoadtestContext(
        String protocol,
        String target,
        int concurrency,
        int connections,
        int vtPerConnection,
        int warmupSeconds,
        int measureSeconds,
        String timestamp,
        @Nullable String label) {

    /**
     * Creates an HTTP loadtest context.
     *
     * @param url the server URL
     * @param concurrency concurrent requests
     * @param warmupSeconds warmup duration
     * @param measureSeconds measurement duration
     * @param timestamp run timestamp
     * @return the context
     */
    public static LoadtestContext http(String url, int concurrency, int warmupSeconds, int measureSeconds,
            String timestamp, @Nullable String label) {
        return new LoadtestContext("HTTP", url, concurrency, 0, 0, warmupSeconds, measureSeconds, timestamp, label);
    }

    /**
     * Creates an RSocket loadtest context.
     *
     * @param host the server host
     * @param port the server port
     * @param connections TCP connections
     * @param vtPerConnection virtual threads per connection
     * @param warmupSeconds warmup duration
     * @param measureSeconds measurement duration
     * @param timestamp run timestamp
     * @return the context
     */
    public static LoadtestContext rsocket(String host, int port, int connections, int vtPerConnection,
            int warmupSeconds, int measureSeconds, String timestamp, @Nullable String label) {
        return new LoadtestContext("RSocket", host + ":" + port, connections * vtPerConnection, connections,
                vtPerConnection, warmupSeconds, measureSeconds, timestamp, label);
    }
}
