/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.prp.inmemory.indexed;

import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.variables.VariableContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Deprecated
public class Variable {

    private final Bool bool;

    private final Bitmask occurencesInCandidates = new Bitmask();

    private final Bitmask unsatisfiableCandidatesWhenFalse = new Bitmask();

    private final Bitmask unsatisfiableCandidatesWhenTrue = new Bitmask();

    public Variable(final Bool bool) {
        this.bool = Preconditions.checkNotNull(bool);
    }

    public Bool getBool() {
        return bool;
    }

    public Bitmask getCandidates() {
        return occurencesInCandidates;
    }

    public Bitmask getUnsatisfiedCandidatesWhenFalse() {
        return unsatisfiableCandidatesWhenFalse;
    }

    public Bitmask getUnsatisfiedCandidatesWhenTrue() {
        return unsatisfiableCandidatesWhenTrue;
    }

    public Mono<Boolean> evaluate(final FunctionContext functionCtx, final VariableContext variableCtx) {
        Mono<Boolean> result = Mono.empty();;
        try {
            result = getBool().evaluate(functionCtx, variableCtx);
        } catch (PolicyEvaluationException e) {
            log.debug(Throwables.getStackTraceAsString(e));
        }
        return result;
    }

    public Optional<Boolean> evaluateBlocking(final FunctionContext functionCtx, final VariableContext variableCtx) {
        Boolean result = null;
        try {
            result = getBool().evaluate(functionCtx, variableCtx).block();
        } catch (PolicyEvaluationException e) {
            log.debug(Throwables.getStackTraceAsString(e));
        }
        return Optional.ofNullable(result);
    }

}
