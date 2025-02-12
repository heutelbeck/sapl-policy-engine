/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.pdp;

import java.io.Serializable;

public record SaplError(String message, String documentName, String source, int offset, int endOffset, int startLine)
        implements Serializable {

    public static final String UNKNOWN_ERROR_MESSAGE = "Unknown Error.";
    public static final SaplError UNKNOWN_ERROR = new SaplError(UNKNOWN_ERROR_MESSAGE, null, null, 0, 0, 0);

    public static SaplError of(String message) {
        return new SaplError(message, null, null, 0, 0, 0);
    }
}
