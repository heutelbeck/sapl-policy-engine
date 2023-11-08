/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class SchemaProposals {

    private static final Collection<String>    unwantedPathKeywords = Set.of("java\\.?");
    private final VariablesAndCombinatorSource variablesAndCombinatorSource;

    public List<String> getVariableNamesAsTemplates() {
        var variables = getAllVariablesAsMap();

        if (!variables.isEmpty())
            return new ArrayList<>(variables.keySet());
        else
            return new ArrayList<>();
    }

    public List<String> getCodeTemplates(Expression expression) {
        return getAllVariables().next()
                .flatMap(vars -> expression.evaluate().contextWrite(ctx -> AuthorizationContext.setVariables(ctx, vars))
                        .flatMap(this::getCodeTemplates).filter(StringUtils::isNotBlank).collectList())
                .block();
    }

    private Flux<String> getCodeTemplates(Val v) {
        return Flux.fromIterable(schemaTemplatesFromJson(v.getText()));
    }

    private Map<String, JsonNode> getAllVariablesAsMap() {
        return getAllVariables().defaultIfEmpty(Map.of()).blockFirst();
    }

    private Flux<Map<String, JsonNode>> getAllVariables() {
        return variablesAndCombinatorSource.getVariables().map(v -> v.orElse(Map.of()));
    }

    public List<String> schemaTemplatesForFunctions(String functionSchema) {
        return new SchemaParser(getAllVariablesAsMap()).generatePaths(functionSchema);
    }

    public List<String> schemaTemplatesForAttributes(String attributeSchema) {
        return new SchemaParser(getAllVariablesAsMap()).generatePaths(attributeSchema);
    }

    public List<String> schemaTemplatesFromJson(String schema) {
        var paths = new SchemaParser(getAllVariablesAsMap()).generatePaths(schema);
        return paths.stream().map(this::removeUnwantedKeywordsFromPath).toList();
    }

    private String removeUnwantedKeywordsFromPath(String path) {
        var alteredPath = path;
        for (String keyword : unwantedPathKeywords) {
            alteredPath = alteredPath.replaceAll(keyword, "");
        }
        return alteredPath;
    }

}
