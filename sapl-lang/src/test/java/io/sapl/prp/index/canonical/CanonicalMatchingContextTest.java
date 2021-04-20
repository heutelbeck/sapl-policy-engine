package io.sapl.prp.index.canonical;

import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalMatchingContextTest {

    private EvaluationContext subscriptionScopedEvaluationContext;

    @BeforeEach
    void setUp() {
        subscriptionScopedEvaluationContext = new EvaluationContext(new AnnotationAttributeContext(),
                new AnnotationFunctionContext(), new HashMap<>());

    }

    @Test
    void test_is_referenced() {
        var candidates = new Bitmask();
        var predicate = new Predicate(new Bool(true));

        var matchingCtx = new CanonicalIndexMatchingContext(0, subscriptionScopedEvaluationContext);

        assertFalse(matchingCtx.isPredicateReferencedInCandidates(predicate));

        predicate.getConjunctions().set(5);
        assertFalse(matchingCtx.isPredicateReferencedInCandidates(predicate));

        candidates.set(5);
        matchingCtx.addCandidates(candidates);
        assertTrue(matchingCtx.isPredicateReferencedInCandidates(predicate));
    }

    @Test
    void testAreAllFunctionsEliminated() {
        var matchingCtx = new CanonicalIndexMatchingContext(2, subscriptionScopedEvaluationContext);

        matchingCtx.increaseNumberOfEliminatedFormulasForConjunction(0, 42);
        assertThat(matchingCtx.areAllFunctionsEliminated(0, 42), is(true));
    }

}
