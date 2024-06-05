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
package io.sapl.server.ce.ui.views.pdpconfig;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * Utilities for encoding a {@link PolicyDocumentCombiningAlgorithm} to a
 * {@link String} for the UI.
 */
@UtilityClass
class PolicyDocumentCombiningAlgorithmEncoding {
    private static final Map<PolicyDocumentCombiningAlgorithm, String> mapping = generateMapping();

    public static String encode(@NonNull PolicyDocumentCombiningAlgorithm entry) {
        return mapping.get(entry);
    }

    public static String[] encode(@NonNull PolicyDocumentCombiningAlgorithm[] policyDocumentCombiningAlgorithm) {
        //@formatter:off
		return Stream.of(policyDocumentCombiningAlgorithm)
				.map(PolicyDocumentCombiningAlgorithmEncoding::encode)
				.toArray(String[]::new);
		//@formatter:on
    }

    public static PolicyDocumentCombiningAlgorithm decode(@NonNull String encodedEntry) {
        for (Map.Entry<PolicyDocumentCombiningAlgorithm, String> entry : mapping.entrySet()) {
            String currentEncodedEntry = entry.getValue();

            if (currentEncodedEntry.equals(encodedEntry)) {
                return entry.getKey();
            }
        }

        throw new IllegalArgumentException(String.format("no entry found for encoded entry: %s", encodedEntry));
    }

    private static Map<PolicyDocumentCombiningAlgorithm, String> generateMapping() {
        PolicyDocumentCombiningAlgorithm[] combiningAlgorithms = PolicyDocumentCombiningAlgorithm.values();

        EnumMap<PolicyDocumentCombiningAlgorithm, String> mapping = new EnumMap<>(
                PolicyDocumentCombiningAlgorithm.class);
        for (PolicyDocumentCombiningAlgorithm combiningAlgorithm : combiningAlgorithms) {
            String encoded = switch (combiningAlgorithm) {
            case DENY_UNLESS_PERMIT -> "deny-unless-permit";
            case PERMIT_UNLESS_DENY -> "permit-unless-deny";
            case ONLY_ONE_APPLICABLE -> "only-one-applicable";
            case DENY_OVERRIDES -> "deny-overrides";
            case PERMIT_OVERRIDES -> "permit-overrides";
            };

            mapping.put(combiningAlgorithm, encoded);
        }

        return mapping;
    }
}
