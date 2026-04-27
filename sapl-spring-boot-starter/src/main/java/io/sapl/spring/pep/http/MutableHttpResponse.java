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
 * before the response is committed to the client. Used as the value type of
 * the response signal and the denial signal.
 * <p>
 * Status, headers, and body are buffered until the surrounding component
 * (PEP filter on the success path, access-denied handler on the deny path)
 * flushes them. Handlers may freely read the controller-produced body via
 * {@link #getBody()} and replace it via {@link #setBody(String)} or
 * {@link #writeBody(String, String)}; header changes go through
 * {@link #setHeader(String, String)}, {@link #addHeader(String, String)},
 * {@link #removeHeader(String)}, or the {@link #headers()} view.
 * <p>
 * Servlet and reactive backends provide their own implementations. Handlers
 * see this interface and write portable code. Cast to a backend type only
 * when a feature outside this interface is required.
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
     * Returns the HTTP status code currently buffered on the response.
     * Returns {@code 200 OK} when no status has been explicitly set.
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
     * Removes all values for {@code name}.
     */
    void removeHeader(String name);

    /**
     * Returns the response headers as currently buffered. Mutations to the
     * returned object are reflected in the outgoing response.
     */
    HttpHeaders headers();

    /**
     * Returns the response body as currently buffered, decoded with the
     * response's character encoding. Reflects bytes written by the
     * controller as well as subsequent {@link #setBody(String)} or
     * {@link #writeBody(String, String)} calls.
     */
    String getBody();

    /**
     * Replaces the buffered body with {@code body}, encoded with the
     * response's character encoding. Does not change the Content-Type
     * header; use {@link #writeBody(String, String)} to set both at once.
     */
    void setBody(String body);

    /**
     * Replaces the buffered body with {@code body} and sets the
     * Content-Type header to {@code contentType}. Convenience for deny
     * handlers and obligation-driven response shaping.
     */
    void writeBody(String contentType, String body);

    /**
     * Returns {@code true} once any mutation method on this response has
     * been called (status, header, body). Used by the access-denied handler
     * to decide whether a handler claimed the denial; callers on the success
     * path do not normally consult this flag.
     */
    boolean isModified();
}
