package io.sapl.pdp;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.DefaultSAPLInterpreter;

class PDPDecisionTests {

    @Test
    void constructor() {
        var subscription = mock(AuthorizationSubscription.class);
        var combined     = mock(CombinedDecision.class);

        var sut = PDPDecision.of(subscription, combined);
        assertThat(sut.getAuthorizationSubscription()).isSameAs(subscription);
        assertThat(sut.getCombinedDecision()).isSameAs(combined);

        var document = mock(SAPL.class);
        var sut2     = PDPDecision.of(subscription, combined, List.of(document));
        assertThat(sut2.getAuthorizationSubscription()).isSameAs(subscription);
        assertThat(sut2.getCombinedDecision()).isSameAs(combined);
        assertThat(sut2.getMatchingDocuments()).contains(document);
    }

    @Test
    void getAuthorizationDecision() {
        var subscription = mock(AuthorizationSubscription.class);
        var combined     = mock(CombinedDecision.class);
        when(combined.getAuthorizationDecision()).thenReturn(AuthorizationDecision.PERMIT);
        var sut = PDPDecision.of(subscription, combined);
        assertThat(sut.getAuthorizationDecision()).isEqualTo(AuthorizationDecision.PERMIT);

        var sut2 = sut.modified(AuthorizationDecision.DENY, "for testing reasons");
        assertThat(sut2.getAuthorizationDecision()).isEqualTo(AuthorizationDecision.DENY);
    }

    @Test
    void getTrace() {
        var subscription = mock(AuthorizationSubscription.class);
        var combined     = mock(CombinedDecision.class);
        var document     = new DefaultSAPLInterpreter().parse("policy \"x\" permit");
        when(combined.getAuthorizationDecision()).thenReturn(AuthorizationDecision.PERMIT);
        TracedDecision sut             = PDPDecision.of(subscription, combined, List.of(document));
        var            unmodifiedTrace = sut.getTrace();
        assertThatJson(unmodifiedTrace).inPath("$." + Trace.OPERATOR).isEqualTo("Policy Decision Point");
        assertThatJson(unmodifiedTrace).inPath("$." + Trace.MODIFICATIONS).isAbsent();

        sut = sut.modified(AuthorizationDecision.DENY, "for testing reasons");
        var modifiedTrace = sut.getTrace();
        assertThatJson(modifiedTrace).inPath("$." + Trace.MODIFICATIONS).isArray().isNotEmpty();
    }

}
