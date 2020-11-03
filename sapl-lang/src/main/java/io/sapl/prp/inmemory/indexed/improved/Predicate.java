package io.sapl.prp.inmemory.indexed.improved;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import io.sapl.prp.inmemory.indexed.Bitmask;
import io.sapl.prp.inmemory.indexed.Bool;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.Optional;

@Slf4j
@Getter
public class Predicate {

    private final Bool bool;

    private final Bitmask conjunctions = new Bitmask();

    private final Bitmask falseForTruePredicate = new Bitmask();

    private final Bitmask falseForFalsePredicate = new Bitmask();

    public Predicate(final Bool bool) {
        this.bool = Preconditions.checkNotNull(bool);
    }

    public Flux<Boolean> evaluateFlux(final FunctionContext functionCtx, final VariableContext variableCtx) {
        try {
            Flux<Boolean> resultFlux = getBool().evaluateFlux(functionCtx, variableCtx);
            return resultFlux;
        } catch (PolicyEvaluationException e) {
            log.debug(Throwables.getStackTraceAsString(e));
        }
        return Flux.empty();
    }

    public Optional<Boolean> evaluate(final FunctionContext functionCtx, final VariableContext variableCtx) {
        Boolean result = null;
        try {
            result = getBool().evaluate(functionCtx, variableCtx);
        } catch (PolicyEvaluationException e) {
            log.debug(Throwables.getStackTraceAsString(e));
        }
        return Optional.ofNullable(result);
    }


}
