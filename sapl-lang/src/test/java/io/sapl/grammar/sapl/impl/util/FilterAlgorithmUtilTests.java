package io.sapl.grammar.sapl.impl.util;

import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import reactor.test.StepVerifier;

class FilterAlgorithmUtilTests {

    @Test
    void errorsAreNotFiltered() {
        var unfiltered     = Val.error("unfiltered");
        var expected       = Val.error("unfiltered");
        var actualFiltered = FilterAlgorithmUtil.applyFilter(unfiltered, 0, null, mock(FilterStatement.class),
                getClass());
        StepVerifier.create(actualFiltered).expectNextMatches(actual -> {
            return actual.equals(expected)
                    && actual.getTrace().get("trace").get("operator").asText().equals("ConditionStep");
        }).verifyComplete();
    }
}
