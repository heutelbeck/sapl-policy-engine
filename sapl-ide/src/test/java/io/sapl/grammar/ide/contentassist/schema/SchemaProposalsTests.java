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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class SchemaProposalsTests {

    @Test
    void noEnvironmentVariablesReturnsEmptyList() {
        var variables = SchemaProposals.getVariableNamesAsTemplates(Map.of());
        assertThat(variables, is(empty()));
    }

    @Test
    void variableNamesAreReturnedWhenExistent() {
        var mapper   = new ObjectMapper();
        var nullNode = mapper.nullNode();
        var vars     = new HashMap<String, JsonNode>();
        vars.put("variableName", nullNode);

        var actual   = SchemaProposals.getVariableNamesAsTemplates(vars);
        var expected = List.of("variableName");
        assertThat(actual, equalTo(expected));
    }
}
