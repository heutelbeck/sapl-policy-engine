package io.sapl.pdp.config;

import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.interpreter.EvaluationContext;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

class PDPConfigurationTest {

    @Test
    void testIsValid() {
        assertThat(new PDPConfiguration(null, null).isValid(), is(false));
        assertThat(new PDPConfiguration(null, mock(CombiningAlgorithm.class)).isValid(), is(false));
        assertThat(new PDPConfiguration(mock(EvaluationContext.class), mock(CombiningAlgorithm.class)).isValid(), is(true));
    }

}
