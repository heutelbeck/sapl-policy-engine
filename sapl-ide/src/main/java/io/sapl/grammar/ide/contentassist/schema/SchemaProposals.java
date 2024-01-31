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

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SchemaProposals {

    public List<String> getVariableNamesAsTemplates(Map<String, JsonNode> variables) {
        var result = new ArrayList<String>(variables.size());
        result.addAll(variables.keySet());
        return result;
    }

    public Collection<String> getCodeTemplates(Expression expression, Map<String, JsonNode> variables) {
        return expression.evaluate().contextWrite(ctx -> AuthorizationContext.setVariables(ctx, variables))
                .map(schema -> SchemaParser.generateProposals(schema, variables)).blockFirst();
    }

}
