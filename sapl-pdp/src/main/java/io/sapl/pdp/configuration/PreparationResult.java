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
package io.sapl.pdp.configuration;

/**
 * The host's decision on whether a configuration's extensions can be deployed.
 */
public sealed interface PreparationResult {

    /** The host accepts the configuration's extension data. */
    record Accepted() implements PreparationResult {}

    /**
     * The host rejects the configuration's extension data with an explanation.
     *
     * @param reason the rejection reason, preserved verbatim
     * @throws IllegalArgumentException if the reason is null or blank
     */
    record Rejected(String reason) implements PreparationResult {

        private static final String ERROR_REJECTION_REASON_REQUIRED = "An extension preparation rejection requires a nonblank reason.";

        public Rejected {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException(ERROR_REJECTION_REASON_REQUIRED);
            }
        }
    }
}
