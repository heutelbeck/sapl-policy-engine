package io.sapl.grammar.ide.contentassist.schema;

import io.sapl.grammar.ide.contentassist.ValueDefinitionProposalExtractionHelper;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValueDefinitionProposalExtractionHelperTests {

    @Test
    void noEnvironmentVariablesReturnsEmptyList (){

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

        var applicationContext      = mock(ContentAssistContext.class);

        var proposals = new ValueDefinitionProposalExtractionHelper(source, functionCtx, attributeCtx, applicationContext);
        var variables = proposals.getFunctionProposals();
        assertThat(variables, is(empty()));
    }

}
