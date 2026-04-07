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
package io.sapl.compiler.index;

import java.io.Serial;

import io.sapl.api.SaplVersion;

/**
 * Thrown when a decision diagram index exceeds the configured maximum
 * node count during construction. Signals the index factory to fall
 * back to a simpler index strategy.
 */
public class IndexSizeLimitExceededException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final String ERROR_INDEX_SIZE_LIMIT = "Index node count %d exceeds limit %d";

    public IndexSizeLimitExceededException(int actualSize, int limit) {
        super(ERROR_INDEX_SIZE_LIMIT.formatted(actualSize, limit));
    }

}
