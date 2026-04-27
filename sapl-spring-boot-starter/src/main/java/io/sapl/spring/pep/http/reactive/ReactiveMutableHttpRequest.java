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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import io.sapl.spring.pep.http.MutableHttpRequest;
import lombok.val;

/**
 * Reactive-backed {@link MutableHttpRequest}. Collects header and attribute
 * mutations applied by constraint handlers, then materialises them as a
 * mutated {@link ServerHttpRequest} (and {@link ServerWebExchange}) when
 * {@link #applyTo(ServerWebExchange)} is called by the PEP filter.
 * <p>
 * The original request is never modified. The PEP filter discards the
 * wrapper and forwards the original request when {@link #isModified()}
 * returns {@code false}.
 */
public final class ReactiveMutableHttpRequest implements MutableHttpRequest {

    private final ServerHttpRequest         original;
    private final Map<String, List<String>> headerOverrides    = new LinkedHashMap<>();
    private final Set<String>               removedHeaders     = new LinkedHashSet<>();
    private final Map<String, Object>       attributeOverrides = new LinkedHashMap<>();

    private boolean modified = false;

    public ReactiveMutableHttpRequest(ServerHttpRequest original) {
        this.original = original;
    }

    @Override
    public void setHeader(String name, String value) {
        removedHeaders.remove(name);
        headerOverrides.put(name, new ArrayList<>(List.of(value)));
        modified = true;
    }

    @Override
    public void addHeader(String name, String value) {
        if (removedHeaders.remove(name)) {
            headerOverrides.put(name, new ArrayList<>(List.of(value)));
            modified = true;
            return;
        }
        val current = headerOverrides.computeIfAbsent(name, k -> {
            val existing = original.getHeaders().get(name);
            return existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        });
        current.add(value);
        modified = true;
    }

    @Override
    public void removeHeader(String name) {
        headerOverrides.remove(name);
        removedHeaders.add(name);
        modified = true;
    }

    @Override
    public void setAttribute(String name, Object value) {
        attributeOverrides.put(name, value);
        modified = true;
    }

    @Override
    public HttpRequest snapshot() {
        return applyToRequest();
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    /**
     * Returns the mutated exchange when any header or attribute mutation
     * happened, otherwise the original exchange unchanged.
     */
    public ServerWebExchange applyTo(ServerWebExchange exchange) {
        if (!modified) {
            return exchange;
        }
        val builder = exchange.mutate();
        if (!headerOverrides.isEmpty() || !removedHeaders.isEmpty()) {
            builder.request(applyToRequest());
        }
        val mutated = builder.build();
        for (val entry : attributeOverrides.entrySet()) {
            mutated.getAttributes().put(entry.getKey(), entry.getValue());
        }
        return mutated;
    }

    private ServerHttpRequest applyToRequest() {
        if (headerOverrides.isEmpty() && removedHeaders.isEmpty()) {
            return original;
        }
        return original.mutate().headers(headers -> {
            for (val name : removedHeaders) {
                headers.remove(name);
            }
            for (val entry : headerOverrides.entrySet()) {
                headers.remove(entry.getKey());
                for (val value : entry.getValue()) {
                    headers.add(entry.getKey(), value);
                }
            }
        }).build();
    }
}
