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

import java.io.Serial;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.sapl.api.SaplVersion;
import io.sapl.api.pdp.configuration.ConfigurationProvenance;

/**
 * Provenance for a configuration loaded from a filesystem directory. Records the
 * directory root, the {@code pdp.json} file it was read from (if any), and the
 * mapping from each policy document to the {@code .sapl} file it came from.
 * <p>
 * Lives beside the directory source that produces it ({@link PDPConfigurationLoader}),
 * mirroring how bundle provenance lives beside the bundle machinery; only the
 * neutral {@link ConfigurationProvenance} contract resides in the API module.
 *
 * @param rootPath the filesystem path of the directory the configuration was
 * loaded from
 * @param pdpJsonPath the filesystem path of the {@code pdp.json} file, or
 * {@code null} if the directory carried no {@code pdp.json}
 * @param documentFiles maps each SAPL policy document to the {@code .sapl} file
 * name it was read from
 */
public record DirectoryProvenance(String rootPath, @Nullable String pdpJsonPath, Map<String, String> documentFiles)
        implements ConfigurationProvenance {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    public DirectoryProvenance {
        documentFiles = documentFiles == null ? Map.of() : Map.copyOf(documentFiles);
    }
}
