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
package io.sapl.interpreter.pip;

import java.lang.reflect.Method;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Metadata for attribute finders.
 */
@Data
@AllArgsConstructor
public class AttributeFinderMetadata implements LibraryEntryMetadata {

    Object   policyInformationPoint;
    Method   function;
    String   libraryName;
    String   functionName;
    JsonNode functionSchema;
    String   documentation;
    boolean  environmentAttribute;
    boolean  attributeWithVariableParameter;
    boolean  varArgsParameters;
    int      numberOfParameters;

    @Override
    public String getDocumentationCodeTemplate(String alias) {
        final var sb                             = new StringBuilder();
        var       indexOfParameterBeingDescribed = 0;

        if (!isEnvironmentAttribute())
            sb.append(describeParameterForDocumentation(indexOfParameterBeingDescribed++)).append('.');

        if (isAttributeWithVariableParameter())
            indexOfParameterBeingDescribed++;

        sb.append('<').append(alias);

        appendParameterList(sb, indexOfParameterBeingDescribed, this::describeParameterForDocumentation);

        sb.append('>');
        return sb.toString();
    }

    @Override
    public String getDocumentationCodeTemplate() {
        return getDocumentationCodeTemplate(fullyQualifiedName());
    }

    @Override
    public String getCodeTemplate(String alias) {
        final var sb = new StringBuilder();
        sb.append('<');

        var indexOfParameterBeingDescribed = 0;

        if (!isEnvironmentAttribute())
            indexOfParameterBeingDescribed++;

        if (isAttributeWithVariableParameter())
            indexOfParameterBeingDescribed++;

        sb.append(alias);

        appendParameterList(sb, indexOfParameterBeingDescribed, this::getParameterName);

        sb.append('>');
        return sb.toString();
    }

    @Override
    public String getCodeTemplate() {
        return getCodeTemplate(fullyQualifiedName());
    }

}
