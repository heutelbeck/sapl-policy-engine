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

import java.io.IOException;
import java.io.Serial;

import org.jspecify.annotations.Nullable;

/**
 * Signals that a request body exceeded the configured size limit while it was
 * being read. Extending {@link IOException} lets it surface through the
 * standard stream-read contract: the bypass-Spring PDP servlets read the body
 * inside {@code catch (IOException | ...)} blocks and can therefore translate
 * this into an HTTP {@code 413 Content Too Large} response, rather than letting
 * a Spring MVC {@code ResponseStatusException} escape unhandled to the
 * container and surface as a generic {@code 500}.
 */
public final class RequestBodyTooLargeException extends IOException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a message describing the configured limit.
     *
     * @param message the detail message
     */
    public RequestBodyTooLargeException(String message) {
        super(message);
    }

    /**
     * Reports whether the given throwable was caused by a request-body overflow.
     * Jackson may wrap the {@link RequestBodyTooLargeException} thrown by the
     * input stream into its own exception, so the whole cause chain is scanned.
     *
     * @param throwable the throwable to inspect, may be null
     * @return true if a {@link RequestBodyTooLargeException} appears in the cause
     * chain
     */
    public static boolean isCausedBy(@Nullable Throwable throwable) {
        for (var current = throwable; current != null; current = current.getCause()) {
            if (current instanceof RequestBodyTooLargeException) {
                return true;
            }
        }
        return false;
    }
}
