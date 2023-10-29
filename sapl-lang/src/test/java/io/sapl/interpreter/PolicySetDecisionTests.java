package io.sapl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;

public class PolicySetDecisionTests {

    @Test
    void error() {
        var decision = PolicySetDecision.error("documentName", "error message");
        assertThat(decision.getTrace().get(Trace.DOCUMENT_TYPE).textValue()).isEqualTo("policy set");
        assertThat(decision.getTrace().get(Trace.POLICY_SET_NAME).textValue()).isEqualTo("documentName");
    }

    @Test
    void ofCombined() {
        var decision = PolicySetDecision
                .of(CombinedDecision.of(AuthorizationDecision.NOT_APPLICABLE, "algorithm name"), "documentName")
                .withTargetResult(Val.TRUE);
        assertThat(decision.getTrace().get(Trace.DOCUMENT_TYPE).textValue()).isEqualTo("policy set");
        assertThat(decision.getTrace().get(Trace.POLICY_SET_NAME).textValue()).isEqualTo("documentName");
        assertThat(decision.getTrace().get(Trace.TARGET).get(Trace.VALUE).asBoolean()).isTrue();
    }

    @Test
    void ofTargetError() {
        var decision = PolicySetDecision.ofTargetError("documentName", Val.error("error message"), "test");
        assertThat(decision.getTrace().get(Trace.TARGET).get(Trace.VALUE).textValue())
                .isEqualTo("|ERROR| error message");
        assertThat(decision.getTrace().get(Trace.POLICY_SET_NAME).textValue()).isEqualTo("documentName");
    }
}
