/**
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
package io.sapl.grammar.ide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pip.ClockPolicyInformationPoint;

public class DefaultLibraryAttributeFinder implements LibraryAttributeFinder {

	private AttributeContext attributeContext;
	private FunctionContext funtionContext;

	public DefaultLibraryAttributeFinder() throws InitializationException {
		attributeContext = new AnnotationAttributeContext();
		attributeContext.loadPolicyInformationPoint(new ClockPolicyInformationPoint());
		funtionContext = new AnnotationFunctionContext();
		funtionContext.loadLibrary(new FilterFunctionLibrary());
		funtionContext.loadLibrary(new StandardFunctionLibrary());
		funtionContext.loadLibrary(new TemporalFunctionLibrary());
	}

	public DefaultLibraryAttributeFinder(EvaluationContext evaluationContext) {
		attributeContext = evaluationContext.getAttributeCtx();
		funtionContext = evaluationContext.getFunctionCtx();
	}

	@Override
	public Collection<String> getAvailableAttributes(String identifier) {

		identifier = identifier.trim().toLowerCase();

		List<String> steps;
		if (identifier.isBlank()) {
			steps = new ArrayList<>();
		} else {
			steps = Arrays.asList(identifier.split("\\."));
		}

		Integer stepCount = steps.size();
		if (stepCount > 0) {
			String lastChar = identifier.substring(identifier.length() - 1);
			if (lastChar.equals("."))
				stepCount++;
		}

		if (stepCount == 0) {
			return getAvailableLibraries();
		} else if (stepCount == 1) {
			Set<String> availableLibraries = new HashSet<>();
			String needle = steps.get(0);
			for (String library : getAvailableLibraries()) {
				if (library.startsWith(needle))
					availableLibraries.add(library);
			}
			return availableLibraries;
		} else {
			Set<String> availableFunctions = new HashSet<>();
			String currentLibrary = steps.get(0);
			String needle = "";
			if (steps.size() > 1)
				needle = steps.get(1);
			for (String function : getAvailableFunctions(currentLibrary)) {
				if (function.startsWith(needle))
					availableFunctions.add(function);
			}
			return availableFunctions;
		}
	}

	private Set<String> getAvailableLibraries() {
		Set<String> availableLibraries = new HashSet<>();
		availableLibraries.addAll(attributeContext.getAvailableLibraries());
		availableLibraries.addAll(funtionContext.getAvailableLibraries());
		return availableLibraries;
	}

	private Set<String> getAvailableFunctions(final String library) {
		Set<String> availableFunctions = new HashSet<>();
		availableFunctions.addAll(attributeContext.providedFunctionsOfLibrary(library));
		availableFunctions.addAll(funtionContext.providedFunctionsOfLibrary(library));
		return availableFunctions;
	}
}
