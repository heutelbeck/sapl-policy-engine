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
package io.sapl.test;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;

public abstract class SaplTestFixtureTemplate implements SaplTestFixture {

	protected final AnnotationAttributeContext attributeCtx = new AnnotationAttributeContext();

	protected final AnnotationFunctionContext functionCtx = new AnnotationFunctionContext();

	protected final Map<String, JsonNode> variables = new HashMap<>(1);

	@Override
	public SaplTestFixture registerPIP(Object pip) throws InitializationException {
		this.attributeCtx.loadPolicyInformationPoint(pip);
		return this;
	}

	@Override
	public SaplTestFixture registerFunctionLibrary(Object library) throws InitializationException {
		this.functionCtx.loadLibrary(library);
		return this;
	}

	@Override
	public SaplTestFixture registerVariable(String key, JsonNode value) {
		if (this.variables.containsKey(key)) {
			throw new SaplTestException("The VariableContext already contains a key \"" + key + "\"");
		}
		this.variables.put(key, value);
		return this;
	}

}
