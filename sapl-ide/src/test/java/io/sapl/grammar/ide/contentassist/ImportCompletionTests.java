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
package io.sapl.grammar.ide.contentassist;

import io.sapl.pip.TimePolicyInformationPoint;
import org.eclipse.xtext.testing.TestCompletionConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests regarding the auto completion of import statements
 */
public class ImportCompletionTests extends CompletionTests {

	@Test
	public void testCompletion_AtTheBeginningImportStatement_ReturnsLibraries() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "import ";
			it.setModel(policy);
			it.setColumn(policy.length());
			it.setAssertCompletionList(completionList -> {
				var expected = List.of("time", "filter", "standard", "time");
				assertProposalsSimple(expected, completionList);
			});
		});
	}

	@Test
	public void testCompletion_WithPartialLibrary_ReturnsLibrary() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "import ti";
			it.setModel(policy);
			it.setColumn(policy.length());
			it.setAssertCompletionList(completionList -> {
				var expected = List.of("time");
				assertProposalsSimple(expected, completionList);
			});
		});
	}

	@Test
	public void testCompletion_WithFullLibrary_ReturnsFunction() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "import time.";
			it.setModel(policy);
			it.setColumn(policy.length());
			it.setAssertCompletionList(completionList -> {
				var expected = Arrays.stream(TimePolicyInformationPoint.class.getDeclaredMethods())
						.filter(method -> Modifier.isPublic(method.getModifiers())).map(Method::getName)
						.collect(Collectors.toList());
				assertProposalsSimple(expected, completionList);
			});
		});
	}

	@Test
	public void testCompletion_WithFullLibraryAndPartialFunction_ReturnsFunction() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "import time.n";
			it.setModel(policy);
			it.setColumn(policy.length());
			it.setAssertCompletionList(completionList -> {
				var expected = Arrays.stream(TimePolicyInformationPoint.class.getDeclaredMethods())
						.filter(method -> Modifier.isPublic(method.getModifiers())).map(Method::getName)
						.filter(label -> label.startsWith("n")).collect(Collectors.toList());
				// var expected = List.of("now");
				assertProposalsSimple(expected, completionList);
			});
		});
	}

	@Test
	public void testCompletion_WithFullLibraryAndPartialFunctionAndNewLinesInBetween_ReturnsFunction() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "import\ntime.\nn";
			it.setModel(policy);
			it.setLine(2);
			it.setColumn(1);
			it.setAssertCompletionList(completionList -> {
				var expected = List.of("now");
				assertProposalsSimple(expected, completionList);
			});
		});
	}

	@Test
	public void testCompletion_WithPrecedingTextAndFullLibraryAndPartialFunction_ReturnsFunction() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "import time.yesterday\nimport time.n";
			String cursor = "import time.n";
			it.setModel(policy);
			it.setLine(1);
			it.setColumn(cursor.length());
			it.setAssertCompletionList(completionList -> {
				var expected = List.of("now");
				assertProposalsSimple(expected, completionList);
			});
		});
	}

	@Test
	public void testCompletion_WithPrecedingAndSucceedingAndFullLibraryAndPartialFunction_ReturnsFunction() {
		testCompletion((TestCompletionConfiguration it) -> {
			String policy = "import time.yesterday\nimport time.n policy \"test policy\" deny";
			String cursor = "import time.n";
			it.setModel(policy);
			it.setLine(1);
			it.setColumn(cursor.length());
			it.setAssertCompletionList(completionList -> {
				var expected = List.of("now");
				assertProposalsSimple(expected, completionList);
			});
		});
	}

}
