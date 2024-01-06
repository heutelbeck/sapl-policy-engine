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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.pdp.config.VariablesAndCombinatorSource;
import reactor.core.publisher.Flux;

class SchemaProposalsTests {

    @Test
    void noEnvironmentVariablesReturnsEmptyList() {
        var source = mock(VariablesAndCombinatorSource.class);
        when(source.getVariables()).thenReturn(Flux.just(Optional.ofNullable(new HashMap<>())));
        var proposals = new SchemaProposals(source);
        var variables = proposals.getVariableNamesAsTemplates();
        assertThat(variables, is(empty()));
    }

    @Test
    void variableCollectionIsNullReturnsEmptyList() {
        var source = mock(VariablesAndCombinatorSource.class);
        when(source.getVariables()).thenReturn(Flux.just(Optional.ofNullable(null)));
        var proposals = new SchemaProposals(source);
        var variables = proposals.getVariableNamesAsTemplates();
        assertThat(variables, is(empty()));
    }

    @Test
    void variableNamesAreReturnedWhenExistent() {
        var mapper   = new ObjectMapper();
        var nullNode = mapper.nullNode();
        var vars     = new HashMap<String, JsonNode>();
        vars.put("variableName", nullNode);

        var source = mock(VariablesAndCombinatorSource.class);
        when(source.getVariables()).thenReturn(Flux.just(Optional.ofNullable(vars)));

        var proposals = new SchemaProposals(source);
        var actual    = proposals.getVariableNamesAsTemplates();
        var expected  = List.of("variableName");
        assertThat(actual, equalTo(expected));
    }
}
