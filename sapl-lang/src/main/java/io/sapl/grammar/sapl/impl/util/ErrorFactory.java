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
package io.sapl.grammar.sapl.impl.util;

import java.util.Objects;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.SaplError;
import io.sapl.grammar.sapl.SAPL;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorFactory {

    public static Val error(EObject errorSource, String errorMessage, Object... args) {
        String formattedMessage = SaplError.UNKNOWN_ERROR_MESSAGE;
        if (errorMessage != null && !errorMessage.isBlank()) {
            formattedMessage = String.format(errorMessage, args);
        }
        if (errorSource == null) {
            return Val.error(SaplError.of(formattedMessage));
        }
        var nodeSet = NodeModelUtils.getNode(errorSource);
        if (nodeSet == null) {
            return Val.error(SaplError.of(formattedMessage));
        }
        var    start          = nodeSet.getOffset();
        var    end            = nodeSet.getEndOffset();
        var    row            = nodeSet.getStartLine();
        var    root           = nodeSet.getRootNode();
        var    documentSource = root.getText();
        String documentName   = null;
        if (root.getSemanticElement() instanceof SAPL sapl) {
            documentName = sapl.getPolicyElement().getSaplName();
        }
        return Val.error(new SaplError(formattedMessage, documentName, documentSource, start, end, row));
    }

    public static Val error(EObject errorSource, Throwable throwable) {
        if (throwable == null) {
            return error(errorSource, SaplError.UNKNOWN_ERROR_MESSAGE);
        }

        return (throwable.getMessage() == null || throwable.getMessage().isBlank())
                ? error(errorSource, throwable.getClass().getSimpleName())
                : error(errorSource, throwable.getMessage());
    }

    public static Val error(String errorMessage, Object... args) {
        return error(null, errorMessage, args);
    }

    public static Val causeOrMessage(EObject location, Throwable t) {
        var cause = t.getCause();
        return error(location, Objects.requireNonNullElse(cause, t).getMessage());
    }

}
