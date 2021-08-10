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
package io.sapl.grammar.ide.contentassist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * This class is used to offer library and function proposals.
 */
@Component
@NoArgsConstructor
@RequiredArgsConstructor
public class DefaultLibraryAttributeFinder implements LibraryAttributeFinder {

	@Autowired @NonNull
	private AttributeContext attributeContext;
	@Autowired @NonNull
	private FunctionContext funtionContext;

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
