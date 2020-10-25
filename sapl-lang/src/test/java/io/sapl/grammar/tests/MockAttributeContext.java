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
package io.sapl.grammar.tests;

import java.util.Collection;
import java.util.Collections;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.AttributeException;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.pip.PolicyInformationPointDocumentation;
import reactor.core.publisher.Flux;

public class MockAttributeContext implements AttributeContext {

	@Override
	public Flux<Val> evaluate(String attribute, Val value, EvaluationContext ctx, Arguments arguments) {
		if ("ATTRIBUTE".equals(attribute)) {
			return Flux.just(Val.of(attribute));
		} else if ("EXCEPTION".equals(attribute)) {
			return Flux.error(new AttributeException());
		} else {
			return Flux.just(value);
		}
	}

	@Override
	public Boolean provides(String function) {
		return true;
	}

	@Override
	public Collection<String> findersInLibrary(String pipName) {
		return Collections.emptyList();
	}

	@Override
	public void loadPolicyInformationPoint(Object pip) throws AttributeException {
		// NOP
	}

	@Override
	public Collection<PolicyInformationPointDocumentation> getDocumentation() {
		return Collections.emptyList();
	}

}
