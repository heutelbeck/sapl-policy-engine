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
package io.sapl.spring.pdp.embedded;

import io.sapl.api.model.PureOperator;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Registers the compiled {@link PureOperator} record implementations for
 * reflection in a GraalVM native image. The policy index compares operators
 * structurally via {@code PureOperator.semanticEquals}, which reads each
 * record's components reflectively. Without these hints, a native image throws
 * when building an index over a non-trivial policy set.
 * <p>
 * The operator records are discovered by scanning their packages, so new
 * operators are covered automatically rather than through a hand-maintained
 * list.
 */
class SaplOperatorRuntimeHints implements RuntimeHintsRegistrar {

    private static final String[] OPERATOR_PACKAGES = { "io.sapl.compiler.expressions", "io.sapl.compiler.policy" };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        for (val typeName : discoverOperatorTypeNames()) {
            hints.reflection().registerType(TypeReference.of(typeName), MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS);
        }
    }

    /**
     * Scans the operator packages for every implementation of
     * {@link PureOperator}. Package-private for testing so a regression that
     * stops discovering operators is caught without building a native image.
     *
     * @return the fully qualified names of all discovered operator types
     */
    static Set<String> discoverOperatorTypeNames() {
        val scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(PureOperator.class));
        val names = new LinkedHashSet<String>();
        for (val basePackage : OPERATOR_PACKAGES) {
            for (val candidate : scanner.findCandidateComponents(basePackage)) {
                val className = candidate.getBeanClassName();
                if (className != null) {
                    names.add(className);
                }
            }
        }
        return names;
    }
}
