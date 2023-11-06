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
    void noEnvironmentVariablesReturnsEmptyList (){
        var source = mock(VariablesAndCombinatorSource.class);
        when(source.getVariables()).thenReturn(Flux.just(Optional.ofNullable(new HashMap<>())));
        var proposals = new SchemaProposals(source);
        var variables = proposals.getVariableNamesAsTemplates();
        assertThat(variables, is(empty()));
    }

    @Test
    void variableCollectionIsNullReturnsEmptyList (){
        var source = mock(VariablesAndCombinatorSource.class);
        when(source.getVariables()).thenReturn(Flux.just(Optional.ofNullable(null)));
        var proposals = new SchemaProposals(source);
        var variables = proposals.getVariableNamesAsTemplates();
        assertThat(variables, is(empty()));
    }

    @Test
    void variableNamesAreReturnedWhenExistent (){
        var mapper = new ObjectMapper();
        var nullNode = mapper.nullNode();
        var vars = new HashMap<String, JsonNode>();
        vars.put("variableName", nullNode);

        var source = mock(VariablesAndCombinatorSource.class);
        when(source.getVariables()).thenReturn(Flux.just(Optional.ofNullable(vars)));

        var proposals = new SchemaProposals(source);
        var actual = proposals.getVariableNamesAsTemplates();
        var expected = List.of("variableName");
        assertThat(actual, equalTo(expected));
    }
}
