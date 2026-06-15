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

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Generates auto-assigned configuration identifiers when none is supplied. The
 * id combines an ISO-8601 timestamp for human-readable ordering with a random
 * UUID for uniqueness, so two ids created in the same millisecond never
 * collide.
 */
public final class ConfigurationIds {

    private ConfigurationIds() {
    }

    /**
     * Builds an id of the form {@code <prefix>-<iso-instant>-<uuid>}.
     *
     * @param prefix a short label for the id source, for example {@code config}
     * @return a unique configuration id
     */
    public static String generate(String prefix) {
        return prefix + '-' + DateTimeFormatter.ISO_INSTANT.format(Instant.now()) + '-' + UUID.randomUUID();
    }
}
