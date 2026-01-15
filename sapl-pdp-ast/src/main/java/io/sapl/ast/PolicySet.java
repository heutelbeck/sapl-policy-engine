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
package io.sapl.ast;

import io.sapl.api.model.SourceLocation;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.policyset.PolicySetMetadata;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A policy set document containing multiple policies.
 *
 * @param imports import statements, empty list if none
 * @param metadata policy set identification metadata (name, pdpId,
 * configurationId, documentId, combiningAlgorithm)
 * @param target optional target expression (the 'for' clause), null if none
 * @param match optional schema condition for matching, null if none
 * @param variables variable definitions at policy set level, empty list if none
 * @param policies the policies in this set, at least one required
 * @param location metadata location
 */
public record PolicySet(
        @NonNull List<Import> imports,
        @NonNull PolicySetMetadata metadata,
        @Nullable Expression target,
        @Nullable SchemaCondition match,
        @NonNull List<VarDef> variables,
        @NonNull List<Policy> policies,
        @NonNull SourceLocation location) implements SaplDocument {

    public PolicySet {
        imports   = List.copyOf(imports);
        variables = List.copyOf(variables);
        policies  = List.copyOf(policies);
        if (policies.isEmpty()) {
            throw new SaplCompilerException("Policy set must contain at least one policy", location);
        }
    }

    @Override
    public String name() {
        return metadata.name();
    }

    public CombiningAlgorithm algorithm() {
        return metadata.combiningAlgorithm();
    }
}
