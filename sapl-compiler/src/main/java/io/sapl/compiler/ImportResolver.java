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
package io.sapl.compiler;

import io.sapl.grammar.sapl.FunctionIdentifier;
import io.sapl.grammar.sapl.Import;
import io.sapl.grammar.sapl.SAPL;
import lombok.experimental.UtilityClass;
import org.eclipse.emf.ecore.EObject;

/**
 * Resolves function and attribute identifiers using SAPL import declarations.
 * <p>
 * Short names (without dots) are resolved against imports in the containing
 * SAPL document. Fully qualified names (with
 * dots) pass through unchanged. Traverses the AST upward to find the containing
 * SAPL document.
 */
@UtilityClass
public class ImportResolver {

    /**
     * Resolves a function identifier to its fully qualified name using imports.
     *
     * @param source
     * AST node used to locate the containing SAPL document
     * @param identifier
     * the function identifier to resolve
     *
     * @return the fully qualified function name, or the short name if no matching
     * import found
     */
    public String resolveFunctionIdentifierByImports(EObject source, FunctionIdentifier identifier) {
        return resolveFunctionReferenceByImports(source, functionIdentifierToReference(identifier));
    }

    private String resolveFunctionReferenceByImports(EObject source, String nameReference) {
        if (nameReference.contains(".")) {
            return nameReference;
        }

        var sapl = getSourceSaplDocument(source);
        if (null == sapl) {
            return nameReference;
        }

        final var imports = sapl.getImports();
        if (null == imports) {
            return nameReference;
        }

        for (var currentImport : imports) {
            final var importedFunction = fullyQualifiedFunctionName(currentImport);
            if (nameReference.equals(currentImport.getFunctionAlias()) || importedFunction.endsWith(nameReference)) {
                return importedFunction;
            }
        }
        return nameReference;
    }

    private String fullyQualifiedFunctionName(Import anImport) {
        final var library = String.join(".", anImport.getLibSteps());
        return library + "." + anImport.getFunctionName();
    }

    private String functionIdentifierToReference(FunctionIdentifier identifier) {
        if (null == identifier) {
            return "";
        }
        return String.join(".", identifier.getNameFragments());
    }

    private SAPL getSourceSaplDocument(EObject source) {
        if (null == source) {
            return null;
        }
        if (source instanceof SAPL sapl) {
            return sapl;
        }
        return getSourceSaplDocument(source.eContainer());
    }

}
