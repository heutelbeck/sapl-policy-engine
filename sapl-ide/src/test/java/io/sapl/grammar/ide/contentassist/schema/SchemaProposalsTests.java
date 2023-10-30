package io.sapl.grammar.ide.contentassist.schema;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import org.hamcrest.collection.IsEmptyCollection;
import org.junit.jupiter.api.Test;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchemaProposalsTests {

    @Test
    void noEnvironmentVariablesReturnsEmptyList (){
        var source = mock(VariablesAndCombinatorSource.class);
        when(source.getVariables()).thenReturn(Flux.just(Optional.ofNullable(new HashMap<>())));
        var proposals = new SchemaProposals(source);
        var variables = proposals.getVariableNamesAsTemplates();
        assertThat(variables, is(empty()));
    }
}
