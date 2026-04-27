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

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

/**
 * A view of the outbound HTTP response that constraint handlers can mutate
 * before the response is committed. Used as the value type of the
 * response-mutation and denial signals.
 * <p>
 * Status and headers may be changed up to the moment the response body is
 * written. Body mutation is not exposed by this interface. For body
 * rewriting on the success path, attach handlers to the deeper layer that
 * produced the body, or wait for a future signal that supports it.
 * <p>
 * Servlet and reactive backends provide their own implementations.
 * Handlers see this interface and write portable code. Cast to a backend
 * type only when a feature outside this interface is required.
 */
public interface MutableHttpResponse {

    /**
     * Sets the HTTP status code on the response.
     */
    void setStatusCode(HttpStatusCode status);

    /**
     * Sets the HTTP status code from a numeric value (such as 302 or 403).
     */
    default void setStatusCode(int statusValue) {
        setStatusCode(HttpStatusCode.valueOf(statusValue));
    }

    /**
     * Returns the HTTP status code currently set on the response. Returns
     * {@code 200 OK} when no status has been explicitly set.
     */
    HttpStatusCode getStatusCode();

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
     * Returns the response headers as currently configured. Mutations to
     * the returned object are reflected in the outgoing response on
     * backends that support it.
     */
    HttpHeaders headers();

    /**
     * Writes the given body to the response and commits it. After this
     * call the response is final and downstream code cannot append further
     * content. Typical use is in a deny handler that needs to render a
     * custom error page or a redirect target. The {@code contentType}
     * value is set on the response (replacing any previous Content-Type).
     */
    void writeBody(String contentType, String body);
}
