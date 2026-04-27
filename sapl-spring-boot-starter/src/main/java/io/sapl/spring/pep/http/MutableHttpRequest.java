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
package io.sapl.spring.pep.http;

import org.springframework.http.HttpRequest;

/**
 * A view of the inbound HTTP request that constraint handlers can mutate
 * before downstream filters and the controller see it. Used as the value
 * type of the request-mutation signal.
 * <p>
 * Header changes are applied to the request the rest of the chain consumes.
 * Attribute changes are visible through the standard request-attribute API
 * downstream. The {@link #snapshot()} method returns a read-only view of
 * the current state for inspection.
 * <p>
 * Servlet and reactive backends provide their own implementations.
 * Handlers see this interface and write portable code. Cast to a backend
 * type only when a feature outside this interface is required.
 */
public interface MutableHttpRequest {

    /**
     * Replaces any existing values for {@code name} with the single value
     * {@code value}.
     */
    void setHeader(String name, String value);

    /**
     * Appends {@code value} to the existing values for {@code name}.
     */
    void addHeader(String name, String value);

    /**
     * Removes all values for {@code name}.
     */
    void removeHeader(String name);

    /**
     * Sets a request-scoped attribute that downstream filters and the
     * controller can read through the standard request-attribute API.
     */
    void setAttribute(String name, Object value);

    /**
     * Returns a read-only view of the request as it currently stands.
     * Reflects mutations made through this interface up to the call.
     */
    HttpRequest snapshot();
}
