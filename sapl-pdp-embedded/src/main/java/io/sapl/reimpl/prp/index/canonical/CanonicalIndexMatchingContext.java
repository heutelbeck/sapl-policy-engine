package io.sapl.reimpl.prp.index.canonical;

import io.sapl.interpreter.EvaluationContext;
import io.sapl.prp.inmemory.indexed.Bitmask;
import lombok.Getter;
import lombok.Setter;

@Getter
public class CanonicalIndexMatchingContext {
    private final Bitmask clauseCandidatesMask;
    private final Bitmask matchingCandidatesMask;

    private final int[] trueLiteralsOfConjunction;
    private final int[] eliminatedFormulasWithConjunction;

    private final EvaluationContext subscriptionScopedEvaluationContext;

    @Setter
    private boolean errorsInTargets = false;

    public CanonicalIndexMatchingContext(CanonicalIndexDataContainer dataContainer,
                                         EvaluationContext subscriptionScopedEvaluationContext) {
        int arrayLength = dataContainer.getNumberOfLiteralsInConjunction().length;

        clauseCandidatesMask = new Bitmask();
        clauseCandidatesMask.set(0, arrayLength);

        matchingCandidatesMask = new Bitmask();

        trueLiteralsOfConjunction = new int[arrayLength];
        eliminatedFormulasWithConjunction = new int[arrayLength];

        this.subscriptionScopedEvaluationContext = subscriptionScopedEvaluationContext;
    }
}
