package io.sapl.prp.index.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;

import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;

public class CanonicalMatchingContextTest {

    private EvaluationContext subscriptionScopedEvaluationContext;

    @Before
    public void setUp() {
        subscriptionScopedEvaluationContext = new EvaluationContext(
                new AnnotationAttributeContext(), new AnnotationFunctionContext(), new HashMap<>());

    }

    @Test
    public void test_is_referenced() {
        var candidates = new Bitmask();
        var predicate = new Predicate(new Bool(true));

        var matchingCtx = new CanonicalIndexMatchingContext(0, subscriptionScopedEvaluationContext);

        assertThat(matchingCtx.isPredicateReferencedInCandidates(predicate)).isFalse();

        predicate.getConjunctions().set(5);
        assertThat(matchingCtx.isPredicateReferencedInCandidates(predicate)).isFalse();

        candidates.set(5);
        matchingCtx.addCandidates(candidates);
        assertThat(matchingCtx.isPredicateReferencedInCandidates(predicate)).isTrue();
    }

}
