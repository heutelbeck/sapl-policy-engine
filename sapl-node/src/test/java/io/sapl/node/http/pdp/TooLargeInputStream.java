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
package io.sapl.node.http.pdp;

import io.sapl.node.http.RequestBodyTooLargeException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

/**
 * Servlet input stream that aborts on first read with a
 * {@link RequestBodyTooLargeException}, mimicking the behaviour of the
 * request-body size cap when a chunked over-limit body is read by a bypass
 * servlet. Used to verify the servlets translate the overflow into HTTP 413.
 */
final class TooLargeInputStream extends ServletInputStream {

    @Override
    public int read() throws RequestBodyTooLargeException {
        throw new RequestBodyTooLargeException("Request body exceeds the configured limit of 64 bytes.");
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        // No asynchronous reads in tests.
    }
}
