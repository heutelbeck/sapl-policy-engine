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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.junit.jupiter.api.Test;

import io.sapl.grammar.ide.contentassist.ValueDefinitionProposalExtractionHelper;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import reactor.core.publisher.Flux;

class ValueDefinitionProposalExtractionHelperTests {

    @Test
    void noEnvironmentVariablesReturnsEmptyList() {

        final String PERSON_SCHEMA = """
                {
                  "type": "object",
                  "properties": {
                	"name": { "type": "string" }
                  }
                }
                """;

        var source = mock(VariablesAndCombinatorSource.class);
        when(source.getVariables()).thenReturn(Flux.just(Optional.ofNullable(new HashMap<>())));

        var functionCtx = mock(FunctionContext.class);
        when(functionCtx.getCodeTemplates()).thenReturn(List.of());
        when(functionCtx.getFunctionSchemas()).thenReturn(Map.of("schemaTest.person", PERSON_SCHEMA));

        var attributeCtx = mock(AttributeContext.class);

        var applicationContext = mock(ContentAssistContext.class);

        var proposals = new ValueDefinitionProposalExtractionHelper(source, functionCtx, attributeCtx,
                applicationContext);
        var variables = proposals.getFunctionProposals();
        assertThat(variables, is(empty()));
    }

}
