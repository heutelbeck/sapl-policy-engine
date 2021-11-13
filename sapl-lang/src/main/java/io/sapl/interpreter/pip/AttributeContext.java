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
package io.sapl.interpreter.pip;

import java.util.Collection;
import java.util.List;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.InitializationException;
import reactor.core.publisher.Flux;

public interface AttributeContext extends LibraryFunctionProvider {

	Flux<Val> evaluateAttribute(String attribute, Val value, EvaluationContext ctx, Arguments arguments);

	Flux<Val> evaluateEnvironmentAttribute(String attribute, EvaluationContext ctx, Arguments arguments);

	void loadPolicyInformationPoint(Object pip) throws InitializationException;

	Collection<PolicyInformationPointDocumentation> getDocumentation();

	List<String> getCodeTemplatesWithPrefix(String prefix, boolean isEnvirionmentAttribute);

}
