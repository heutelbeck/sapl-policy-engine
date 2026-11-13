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
package io.sapl.node.cli.commands;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import lombok.val;

final class AttributeApiStub implements AutoCloseable {
    record ApiRequest(String method, URI uri, Headers headers, String body) {}

    private final HttpServer       apiServer;
    private final List<ApiRequest> requests     = new CopyOnWriteArrayList<>();
    private volatile int           status       = 200;
    private volatile String        responseBody = "";

    AttributeApiStub() throws IOException {
        apiServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        apiServer.createContext("/", httpContext -> {
            val body = new String(httpContext.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new ApiRequest(httpContext.getRequestMethod(), httpContext.getRequestURI(),
                    httpContext.getRequestHeaders(), body));

            val bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            httpContext.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);

            if (bytes.length > 0) {
                httpContext.getResponseBody().write(bytes);
            }
            httpContext.close();
        });
        apiServer.start();
    }

    String url() {
        return "http://localhost:" + apiServer.getAddress().getPort();
    }

    void respondWith(int status, String body) {
        this.status       = status;
        this.responseBody = body;
    }

    ApiRequest lastRequest() {
        return requests.getLast();
    }

    @Override
    public void close() {
        apiServer.stop(0);
    }
}
