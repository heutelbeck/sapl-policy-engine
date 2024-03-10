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
package io.sapl.prp;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DocumentType;

public record Document(String id, String name, SAPL sapl, Diagnostic diagnostic, String errorMessage) {

    public boolean isInvalid() {
        return diagnostic == null || diagnostic.getSeverity() != Diagnostic.OK;
    }

    public String source() {
        return NodeModelUtils.findActualNodeFor(sapl).getText();
    }

    public DocumentType type() {
        if (isInvalid()) {
            return DocumentType.INVALID;
        } else if (sapl.getPolicyElement() instanceof Policy) {
            return DocumentType.POLICY;
        } else {
            return DocumentType.POLICY_SET;
        }
    }
}
