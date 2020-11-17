/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.grammar.sapl.impl;

import java.util.function.Function;

import org.reactivestreams.Publisher;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.FilterStatement;
import io.sapl.interpreter.EvaluationContext;
import lombok.NonNull;
import reactor.core.publisher.Flux;

public class FilterExtendedImplCustom extends FilterExtendedImpl {

	@Override
	public Flux<Val> apply(Val unfilteredValue, EvaluationContext ctx, @NonNull Val relativeNode) {
		if (unfilteredValue.isError()) {
			return Flux.just(unfilteredValue);
		}
		if (unfilteredValue.isUndefined()) {
			return Val.errorFlux(FILTERS_CANNOT_BE_APPLIED_TO_UNDEFINED_VALUES);
		}
		if (statements == null) {
			return Flux.just(unfilteredValue);
		}
		return Flux.just(unfilteredValue).switchMap(applyFilterStatements(ctx, relativeNode));
	}

	private Function<? super Val, Publisher<? extends Val>> applyFilterStatements(EvaluationContext ctx,
			Val relativeNode) {
		return applyFilterStatements(0, ctx, relativeNode);
	}

	private Function<? super Val, Publisher<? extends Val>> applyFilterStatements(int statementId,
			EvaluationContext ctx, Val relativeNode) {
		if (statementId == statements.size()) {
			return Flux::just;
		}
		return value -> applyFilterStatement(value, statements.get(statementId), ctx, relativeNode)
				.switchMap(applyFilterStatements(statementId + 1, ctx, relativeNode));
	}

	private Flux<Val> applyFilterStatement(Val unfilteredValue, FilterStatement statement, EvaluationContext ctx,
			Val relativeNode) {
		if (statement.getTarget().getSteps().size() == 0) {
			// the expression has no steps. apply filter to unfiltered node directly
			return applyFilterFunction(unfilteredValue, statement.getArguments(),
					FunctionUtil.resolveAbsoluteFunctionName(statement.getFsteps(), ctx), ctx, relativeNode,
					statement.isEach());
		} else {
			// descent with steps
			return statement.getTarget().getSteps().get(0).applyFilterStatement(unfilteredValue, ctx, relativeNode, 0,
					statement);
		}
	}

}
