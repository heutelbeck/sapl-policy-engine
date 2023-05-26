/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.ImportsUtil;
import io.sapl.interpreter.DocumentEvaluationResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SAPLImplCustom extends SAPLImpl {


	@Override
	public Mono<Val> matches() {
		return getPolicyElement().matches();
	}

	@Override
	public Flux<DocumentEvaluationResult> evaluate() {
		return policyElement.evaluate().contextWrite(ctx -> ImportsUtil.loadImportsIntoContext(this, ctx))
				.onErrorResume(this::importFailure);
	}

	private Flux<DocumentEvaluationResult> importFailure(Throwable error) {
		return Flux.just(policyElement.importError(error.getMessage()));
	}

	@Override
	public String toString() {
		return getPolicyElement().getSaplName();
	}

}
