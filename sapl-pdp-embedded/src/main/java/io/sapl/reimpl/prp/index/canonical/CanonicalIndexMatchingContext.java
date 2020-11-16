package io.sapl.reimpl.prp.index.canonical;

import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import io.sapl.prp.inmemory.indexed.Bitmask;
import lombok.Getter;

@Getter
public class CanonicalIndexMatchingContext {
    private final Bitmask clauseCandidatesMask;

    private final int[] trueLiteralsOfConjunction;
    private final int[] eliminatedFormulasWithConjunction;

    private final FunctionContext functionCtx;
    private final VariableContext variableCtx;

    public CanonicalIndexMatchingContext(CanonicalIndexDataContainer dataContainer,
                                         FunctionContext functionCtx, VariableContext variableCtx) {
        int arrayLength = dataContainer.getNumberOfLiteralsInConjunction().length;

        clauseCandidatesMask = new Bitmask();
        clauseCandidatesMask.set(0, arrayLength);

        trueLiteralsOfConjunction = new int[arrayLength];
        eliminatedFormulasWithConjunction = new int[arrayLength];

        this.functionCtx = functionCtx;
        this.variableCtx = variableCtx;
    }
}
