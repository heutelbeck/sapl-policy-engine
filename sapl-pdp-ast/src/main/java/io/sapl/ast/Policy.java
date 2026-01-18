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
import lombok.NonNull;

import java.util.List;

/**
 * A single policy document.
 *
 * @param imports import statements, empty list if none
 * @param metadata policy identification voterMetadata (name, pdpId,
 * configurationId,
 * documentId)
 * @param entitlement PERMIT or DENY
 * @param body policy body with statements and voterMetadata location
 * @param obligations obligation expressions, empty list if none
 * @param advice advice expressions, empty list if none
 * @param transformation transformation expression, or null if none
 * @param location voterMetadata location
 */
public record Policy(
        @NonNull List<Import> imports,
        @NonNull VoterMetadata metadata,
        @NonNull Entitlement entitlement,
        @NonNull PolicyBody body,
        @NonNull List<Expression> obligations,
        @NonNull List<Expression> advice,
        Expression transformation,
        @NonNull SourceLocation location) implements SaplDocument {

    @Override
    public String name() {
        return metadata.name();
    }
}
