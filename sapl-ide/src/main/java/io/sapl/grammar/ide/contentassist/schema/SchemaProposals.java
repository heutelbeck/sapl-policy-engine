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
package io.sapl.grammar.ide.contentassist.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SchemaProposals {

    private static final Collection<String> UNWANTED_PATH_KEYWORDS = Set.of("java\\.?");
    private final Map<String, JsonNode>     variables;

    public List<String> getVariableNamesAsTemplates() {
        var variablesMap = variables;
        var result       = new ArrayList<String>(variablesMap.size());
        result.addAll(variablesMap.keySet());
        return result;
    }

    public List<String> getCodeTemplates(Expression expression) {
        return expression.evaluate().contextWrite(ctx -> AuthorizationContext.setVariables(ctx, variables))
                .map(this::getCodeTemplates).blockFirst();
    }

    private List<String> getCodeTemplates(Val v) {
        if (v.isDefined()) {
            return schemaTemplatesFromJson(v.get());
        } else {
            return List.of();
        }
    }

    public List<String> schemaTemplatesForFunctions(JsonNode functionSchema) {
        return new SchemaParser(variables).generatePaths(functionSchema);
    }

    public List<String> schemaTemplatesForAttributes(JsonNode attributeSchema) {
        return new SchemaParser(variables).generatePaths(attributeSchema);
    }

    public List<String> schemaTemplatesFromJson(JsonNode schema) {
        var paths = new SchemaParser(variables).generatePaths(schema);
        return paths.stream().map(this::removeUnwantedKeywordsFromPath).filter(StringUtils::isNotBlank).toList();
    }

    private String removeUnwantedKeywordsFromPath(String path) {
        var alteredPath = path;
        for (String keyword : UNWANTED_PATH_KEYWORDS) {
            alteredPath = alteredPath.replaceAll(keyword, "");
        }
        return alteredPath;
    }

}
