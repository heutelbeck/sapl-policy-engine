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
package io.sapl.api.pdp.configuration;

import java.io.Serial;

import io.sapl.api.SaplVersion;

/**
 * The default provenance sentinel for configurations whose origin is not
 * tracked: programmatically assembled configurations, configurations received
 * over the wire from a remote source, resource-loaded configurations,
 * deserialized configurations, and test fixtures.
 * <p>
 * Carries no data. Prefer the shared {@link #INSTANCE} to avoid allocation.
 */
public record UnknownProvenance() implements ConfigurationProvenance {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /** The shared, allocation-free instance of the unknown-provenance sentinel. */
    public static final UnknownProvenance INSTANCE = new UnknownProvenance();
}
