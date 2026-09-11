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
package io.sapl.pdp.configuration.bundle;

import java.io.Serial;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.sapl.api.SaplVersion;
import io.sapl.api.pdp.configuration.ConfigurationProvenance;

/**
 * Provenance for a configuration loaded from a signed bundle. A
 * secret-excluded projection of the bundle content: it records the policy
 * sources, the {@code pdp.json}, the cleartext extension payloads, the
 * critical-extensions declaration, and the manifest, so a consumer can inspect
 * the bundle's signature, creation time, audience, configuration id, and file
 * hashes. It deliberately carries no secret material: the plaintext secrets
 * file, the extension secrets, and the sealed extension secrets are all
 * omitted.
 * <p>
 * Lives beside the bundle machinery that produces it ({@link Bundle}),
 * mirroring how {@code DirectoryProvenance} lives beside the directory source;
 * only the neutral {@link ConfigurationProvenance} contract resides in the API
 * module.
 *
 * @param source the identifier the bundle was loaded under, that is the target
 * PDP id
 * @param documentSources maps each SAPL policy file name to its policy source
 * text
 * @param rawPdpJson the raw {@code pdp.json} content of the bundle
 * @param rawExtensions maps each cleartext extension name to its raw JSON
 * payload
 * @param criticalExtensionsJson the raw critical-extensions declaration, or
 * {@code null} if the bundle carried none
 * @param manifest the bundle manifest carrying the signature, creation time,
 * audience, configuration id, and per-file hashes
 */
public record BundleProvenance(
        String source,
        Map<String, String> documentSources,
        String rawPdpJson,
        Map<String, String> rawExtensions,
        @Nullable String criticalExtensionsJson,
        BundleManifest manifest) implements ConfigurationProvenance {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    public BundleProvenance {
        documentSources = documentSources == null ? Map.of() : Map.copyOf(documentSources);
        rawExtensions   = rawExtensions == null ? Map.of() : Map.copyOf(rawExtensions);
    }
}
