package io.sapl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

public class CombinedDecisionTests {

    @Test
    void error() {
        var decision = CombinedDecision.error("algorithm", "error message");
        assertThat(decision.getAuthorizationDecision().getDecision()).isEqualTo(Decision.INDETERMINATE);
        assertThat(decision.getTrace().get(Trace.COMBINING_ALGORITHM).textValue()).isEqualTo("algorithm");
        assertThat(decision.getTrace().get(Trace.ERROR_MESSAGE).textValue()).isEqualTo("error message");
    }

    @Test
    void ofOneDecision() {
        var decision = CombinedDecision.of(AuthorizationDecision.PERMIT, "test");
        assertThat(decision.getAuthorizationDecision().getDecision()).isEqualTo(Decision.PERMIT);
        assertThat(decision.getTrace().get(Trace.COMBINING_ALGORITHM).textValue()).isEqualTo("test");
    }

    @Test
    void actualCombination() {
        var decision = CombinedDecision.of(AuthorizationDecision.DENY, "test",
                List.of(mock(DocumentEvaluationResult.class)));
        assertThat(decision.getAuthorizationDecision().getDecision()).isEqualTo(Decision.DENY);
        assertThat(decision.getTrace().get(Trace.COMBINING_ALGORITHM).textValue()).isEqualTo("test");
        assertThat(decision.getTrace().get(Trace.EVALUATED_POLICIES).isArray()).isTrue();
    }

}
