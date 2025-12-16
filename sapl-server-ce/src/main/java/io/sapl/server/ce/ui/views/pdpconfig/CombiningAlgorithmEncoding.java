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
package io.sapl.server.ce.ui.views.pdpconfig;

import io.sapl.api.pdp.CombiningAlgorithm;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Utilities for encoding a {@link CombiningAlgorithm} to a {@link String} for
 * the UI.
 */
@UtilityClass
class CombiningAlgorithmEncoding {
    private static final Map<CombiningAlgorithm, String> mapping = generateMapping();

    public static String encode(@NonNull CombiningAlgorithm entry) {
        return mapping.get(entry);
    }

    public static String[] encode(@NonNull CombiningAlgorithm[] combiningAlgorithms) {
        return Stream.of(combiningAlgorithms).map(CombiningAlgorithmEncoding::encode).toArray(String[]::new);
    }

    public static CombiningAlgorithm decode(@NonNull String encodedEntry) {
        for (Map.Entry<CombiningAlgorithm, String> entry : mapping.entrySet()) {
            String currentEncodedEntry = entry.getValue();

            if (currentEncodedEntry.equals(encodedEntry)) {
                return entry.getKey();
            }
        }

        throw new IllegalArgumentException("no entry found for encoded entry: %s".formatted(encodedEntry));
    }

    private static Map<CombiningAlgorithm, String> generateMapping() {
        CombiningAlgorithm[] combiningAlgorithms = CombiningAlgorithm.values();

        EnumMap<CombiningAlgorithm, String> algorithmMapping = new EnumMap<>(CombiningAlgorithm.class);
        for (CombiningAlgorithm combiningAlgorithm : combiningAlgorithms) {
            String encoded = switch (combiningAlgorithm) {
            case DENY_UNLESS_PERMIT  -> "deny-unless-permit";
            case PERMIT_UNLESS_DENY  -> "permit-unless-deny";
            case ONLY_ONE_APPLICABLE -> "only-one-applicable";
            case DENY_OVERRIDES      -> "deny-overrides";
            case PERMIT_OVERRIDES    -> "permit-overrides";
            };

            algorithmMapping.put(combiningAlgorithm, encoded);
        }

        return algorithmMapping;
    }
}
