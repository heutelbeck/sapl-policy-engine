/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.model.pdpconfiguration;

import io.sapl.api.SaplVersion;
import lombok.NonNull;

/**
 * Exception thrown if a provided JSON value is invalid.
 */
public class InvalidJsonException extends Exception {

    private static final long serialVersionUID = SaplVersion.VERISION_UID;

    public InvalidJsonException(@NonNull String invalidJson) {
        this(invalidJson, null);
    }

    public InvalidJsonException(@NonNull String invalidJson, Throwable innerEx) {
        super(String.format("the provided JSON is invalid: %s", invalidJson));
    }
}
