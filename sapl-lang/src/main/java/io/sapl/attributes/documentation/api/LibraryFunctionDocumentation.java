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
package io.sapl.attributes.documentation.api;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.NonNull;

public record LibraryFunctionDocumentation(
        @NonNull String namespace,
        @NonNull String attributeName,
        @NonNull FunctionType type,
        @NonNull String documentationMarkdown,
        ParameterDocumentation entityDocumentation,
        @NonNull List<ParameterDocumentation> parameterDocumentations,
        JsonNode returnTypeSchema) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public LibraryFunctionDocumentation {
        if (type == FunctionType.FUNCTION && entityDocumentation != null) {
            throw new IllegalArgumentException(
                    "The documentation of a function must not habe an entity documentation.");
        }
        if (type == FunctionType.ENVIRONMENT_ATTRIBUTE && entityDocumentation != null) {
            throw new IllegalArgumentException(
                    "The documentation of an environment attribute must not habe an entity documentation.");
        }
    }

    public String codeTemplate() {
        return codeTemplate(LibraryFunctionDocumentation::fullyQualifiedName,
                ParameterDocumentation::parameterDescription);
    }

    public String aliasCodeTemplate(String alias) {
        if (alias != null && !alias.isBlank()) {
            return codeTemplate(f -> alias, ParameterDocumentation::parameterDescription);
        }
        return codeTemplate();
    }

    public String codeTemplateWithTypeIdicators() {
        return entityDocumentation.typedParameterDescription() + '.' + codeTemplate(
                LibraryFunctionDocumentation::fullyQualifiedName, ParameterDocumentation::typedParameterDescription);
    }

    public String fullyQualifiedName() {
        return namespace + '.' + attributeName;
    }

    private String codeTemplate(Function<LibraryFunctionDocumentation, String> namingStrategy,
            Function<ParameterDocumentation, String> parameterDocumentationStrategy) {
        final var sb = new StringBuilder();
        if (type != FunctionType.FUNCTION) {
            sb.append('<');
        }
        sb.append(namingStrategy.apply(this));
        if (!parameterDocumentations.isEmpty()) {
            sb.append('(');
            sb.append(parameterDocumentations.stream().map(parameterDocumentationStrategy)
                    .collect(Collectors.joining(",")));
            sb.append(')');
        }
        if (type != FunctionType.FUNCTION) {
            sb.append('>');
        }
        return sb.toString();
    }

    public String documentationMarkdown() {
        final var sb = new StringBuilder();
        if (null != entityDocumentation) {
            sb.append("\n# Input Entity of Attribute Finder\n\n");
            sb.append(entityDocumentation.parameterDocumentationMarkdown());
        }
        if (!parameterDocumentations.isEmpty()) {
            sb.append("\n# Parameters of ");
            if (type == FunctionType.FUNCTION) {
                sb.append("Function\n\n");
            } else {
                sb.append("Attribute Finder\n\n");
            }
            for (var p : parameterDocumentations) {
                sb.append(p.parameterDocumentationMarkdown());
            }
        }
        if (null != returnTypeSchema) {
            sb.append("\n# Schema of Return Value\n\n");
            sb.append("```JSON\n");
            try {
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(returnTypeSchema);
            } catch (JsonProcessingException e) {
                sb.append("Error processing schema: ");
                sb.append(e.getMessage());
                sb.append('\n');
            }
            sb.append("```\n");
        }
        sb.append(documentationMarkdown);
        return sb.toString();
    }

}
