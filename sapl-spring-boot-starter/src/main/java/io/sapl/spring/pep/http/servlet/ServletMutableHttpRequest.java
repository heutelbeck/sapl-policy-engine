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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;

import io.sapl.spring.pep.http.MutableHttpRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.val;

/**
 * Servlet-backed {@link MutableHttpRequest}. Wraps an inbound
 * {@link HttpServletRequest} and exposes header and attribute mutations to
 * constraint handlers. The wrapped request is what downstream filters and
 * the controller see.
 */
public final class ServletMutableHttpRequest extends HttpServletRequestWrapper implements MutableHttpRequest {

    private final Map<String, List<String>> overrides = new LinkedHashMap<>();
    private final Set<String>               removed   = new LinkedHashSet<>();

    public ServletMutableHttpRequest(HttpServletRequest delegate) {
        super(delegate);
    }

    @Override
    public void setHeader(String name, String value) {
        val key = canonical(name);
        removed.remove(key);
        overrides.put(key, new ArrayList<>(List.of(value)));
    }

    @Override
    public void addHeader(String name, String value) {
        val key = canonical(name);
        if (removed.remove(key)) {
            overrides.put(key, new ArrayList<>(List.of(value)));
            return;
        }
        val current = overrides.computeIfAbsent(key, k -> new ArrayList<>(headerValuesFromDelegate(name)));
        current.add(value);
    }

    @Override
    public void removeHeader(String name) {
        val key = canonical(name);
        overrides.remove(key);
        removed.add(key);
    }

    @Override
    public void setAttribute(String name, Object value) {
        super.setAttribute(name, value);
    }

    @Override
    public HttpRequest snapshot() {
        return new ServletServerHttpRequest(this);
    }

    @Override
    public String getHeader(String name) {
        val key = canonical(name);
        if (removed.contains(key)) {
            return null;
        }
        val override = overrides.get(key);
        if (override != null) {
            return override.isEmpty() ? null : override.getFirst();
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        val key = canonical(name);
        if (removed.contains(key)) {
            return Collections.emptyEnumeration();
        }
        val override = overrides.get(key);
        if (override != null) {
            return Collections.enumeration(override);
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        val names         = new LinkedHashSet<String>();
        val originalNames = super.getHeaderNames();
        if (originalNames != null) {
            while (originalNames.hasMoreElements()) {
                val original = originalNames.nextElement();
                if (!removed.contains(canonical(original))) {
                    names.add(original);
                }
            }
        }
        for (val overrideKey : overrides.keySet()) {
            if (names.stream().noneMatch(existing -> canonical(existing).equals(overrideKey))) {
                names.add(overrideKey);
            }
        }
        return Collections.enumeration(names);
    }

    @Override
    public int getIntHeader(String name) {
        val value = getHeader(name);
        return value == null ? -1 : Integer.parseInt(value);
    }

    @Override
    public long getDateHeader(String name) {
        if (overrides.containsKey(canonical(name)) || removed.contains(canonical(name))) {
            val value = getHeader(name);
            return value == null ? -1L : Long.parseLong(value);
        }
        return super.getDateHeader(name);
    }

    private static String canonical(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private List<String> headerValuesFromDelegate(String name) {
        val values      = new ArrayList<String>();
        val enumeration = super.getHeaders(name);
        if (enumeration != null) {
            while (enumeration.hasMoreElements()) {
                values.add(enumeration.nextElement());
            }
        }
        return values;
    }
}
