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

import io.sapl.api.pdp.configuration.PDPConfiguration;

/**
 * The default {@link ExtensionsProcessor} for a PDP with no extension
 * capabilities. It declares an empty capability set, so it accepts any
 * configuration that requires none and rejects any that declares a critical
 * capability it cannot deploy. {@link #commit} and {@link #remove} are no-ops.
 * This keeps a plain PDP neutral to the extension feature while still refusing
 * to go live on a configuration whose critical dependency it cannot satisfy.
 */
public final class EmptyExtensionsProcessor implements ExtensionsProcessor {

    @Override
    public boolean prepare(String pdpId, PDPConfiguration configuration) {
        return configuration.criticalExtensions().isEmpty();
    }

    @Override
    public void commit(String pdpId, PDPConfiguration configuration) {
        // No capabilities to deploy.
    }

    @Override
    public void remove(String pdpId) {
        // No capabilities to tear down.
    }
}
