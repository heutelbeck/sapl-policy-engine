/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import lombok.NonNull;
import reactor.core.publisher.Flux;

/**
 * Implements the evaluation of relative expressions.
 *
 * Grammar: {BasicRelative} '@' steps+=Step*;
 */
public class BasicRelativeImplCustom extends BasicRelativeImpl {

	private static final String NOT_ALLOWED = "Relative expression error. No relative node.";

	@Override
	public Flux<Val> evaluate( @NonNull Val relativeNode) {
		if (relativeNode.isUndefined()) {
			return Val.errorFlux(NOT_ALLOWED);
		}
		return Flux.just(relativeNode).switchMap(resolveStepsFiltersAndSubTemplates(steps, relativeNode));
	}

}
