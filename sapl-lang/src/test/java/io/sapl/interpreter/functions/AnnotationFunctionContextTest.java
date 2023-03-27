/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.functions;

import static com.spotify.hamcrest.pojo.IsPojo.pojo;
import static io.sapl.hamcrest.Matchers.valError;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Text;
import io.sapl.interpreter.InitializationException;
import lombok.NoArgsConstructor;

class AnnotationFunctionContextTest {

	@Test
	void failToInitializeNonFunctionLibraryAnnotatedClass() {
		assertThrows(InitializationException.class, () -> new AnnotationFunctionContext(""));
	}

	@Test
	void givenLibraryWithNoExplicitNameInAnnotationWhenDocumentationIsReadThenReturnsClassName()
			throws InitializationException {
		AnnotationFunctionContext context = new AnnotationFunctionContext(new FunctionLibraryWithoutName());
		assertThat(context.getDocumentation(), contains(pojo(LibraryDocumentation.class).withProperty("name",
				is(FunctionLibraryWithoutName.class.getSimpleName()))));
	}

	@Test
	void givenMockLibraryWhenListingFunctionsTheMockFunctionIsAvailable() throws InitializationException {
		AnnotationFunctionContext context = new AnnotationFunctionContext(new MockLibrary());
		assertThat(context.providedFunctionsOfLibrary(MockLibrary.LIBRARY_NAME), hasItems(MockLibrary.FUNCTION_NAME));
	}

	@Test
	void givenNoLibrariesWhenListingFunctionForALibraryCollectionIsEmpty() {
		assertThat(new AnnotationFunctionContext().providedFunctionsOfLibrary(null), empty());
	}

	@Test
	void simpleFunctionCallNoParameters() throws InitializationException {
		AnnotationFunctionContext context = new AnnotationFunctionContext(new MockLibrary());
		assertThat(context.evaluate(MockLibrary.LIBRARY_NAME + "." + MockLibrary.FUNCTION_NAME),
				is(MockLibrary.RETURN_VALUE));
	}

	@Test
	void simpleFunctionCallWithParameters() throws InitializationException {
		AnnotationFunctionContext context = new AnnotationFunctionContext(new MockLibrary());
		assertThat(context.evaluate(MockLibrary.LIBRARY_NAME + ".helloTwoArgs", Val.TRUE, Val.FALSE),
				is(MockLibrary.RETURN_VALUE));
	}

	@Test
	void simpleFunctionCallWithVarArgsParameters() throws InitializationException {
		AnnotationFunctionContext context = new AnnotationFunctionContext(new MockLibrary());
		assertThat(context.evaluate(MockLibrary.LIBRARY_NAME + ".helloVarArgs", Val.TRUE, Val.FALSE, Val.UNDEFINED),
				is(MockLibrary.RETURN_VALUE));
	}

	@Test
	void validationForFixedParametersFailsOnWrongInput() throws InitializationException {
		AnnotationFunctionContext context = new AnnotationFunctionContext(new ValidationLibrary());
		assertThat(context.evaluate("validate.fixed", Val.of(0)), valError());
	}

	@Test
	void validationForVarArgsParametersFailsOnWrongInput() throws InitializationException {
		AnnotationFunctionContext context = new AnnotationFunctionContext(new ValidationLibrary());
		assertThat(context.evaluate("validate.varArgs", Val.of(""), Val.of(1)), valError());
	}

	@Test
	void callingFunctionReturningExceptionReturnsError() throws InitializationException {
		AnnotationFunctionContext context = new AnnotationFunctionContext(new MockLibrary());
		assertThat(context.evaluate(MockLibrary.LIBRARY_NAME + ".helloFailure", Val.TRUE, Val.TRUE, Val.TRUE),
				valError());
	}

	@Test
	void simpleFunctionCallNoParametersBadParameterNumberReturnsError() throws InitializationException {
		AnnotationFunctionContext context = new AnnotationFunctionContext(new MockLibrary());
		assertThat(context.evaluate(MockLibrary.LIBRARY_NAME + "." + MockLibrary.FUNCTION_NAME, Val.TRUE), valError());
	}

	@Test
	void loadedFunctionShouldBeProvided() throws InitializationException {
		AnnotationFunctionContext context = new AnnotationFunctionContext(new MockLibrary());
		assertAll(
				() -> assertThat(context.isProvidedFunction(MockLibrary.LIBRARY_NAME + "." + MockLibrary.FUNCTION_NAME),
						is(Boolean.TRUE)),
				() -> assertThat(context.isProvidedFunction(MockLibrary.LIBRARY_NAME + ".helloTwoArgs"), is(Boolean.TRUE)),
				() -> assertThat(context.isProvidedFunction(MockLibrary.LIBRARY_NAME + ".helloVarArgs"), is(Boolean.TRUE)));
	}

	@Test
	void libsTest() throws InitializationException {
		AnnotationFunctionContext context = new AnnotationFunctionContext();
		assertThat(context.evaluate("i.am.not.a.function"), valError());
	}

	@Test
	void failToInitializeWithBadParametersInLibrary() {
		assertThrows(InitializationException.class,
				() -> new AnnotationFunctionContext(new BadParameterTypeFunctionLibrary()));
	}

	@Test
	void failToInitializeWithBadParametersInLibraryVarArgs() {
		assertThrows(InitializationException.class,
				() -> new AnnotationFunctionContext(new BadParameterTypeFunctionLibraryVarArgs()));
	}

	@Test
	void failToInitializeWithBadReturnTypeInLibrary() {
		assertThrows(InitializationException.class,
				() -> new AnnotationFunctionContext(new BadReturnTypeFunctionLibrary()));
	}

	@Test
	void loadedLibrariesShouldBeReturned() throws InitializationException {
		AnnotationFunctionContext context = new AnnotationFunctionContext(new MockLibrary());
		assertThat(context.getAvailableLibraries().contains(MockLibrary.LIBRARY_NAME), is(Boolean.TRUE));
	}

	@Test
	void loadedLibrariesReturnEmptyListWhenNotLoaded() throws InitializationException {
		AnnotationFunctionContext context = new AnnotationFunctionContext();
		assertThat(context.getAvailableLibraries().size(), is(0));
	}

	@Test
	void codeTemplatesAreGenerated() throws InitializationException {
		@FunctionLibrary(name = "test")
		class TestLib {

			@Function
			public Val hello() {
				return Val.TRUE;
			}

			@Function
			public Val helloVarArgs(Val... aVarArgs) {
				return Val.TRUE;
			}

			@Function
			public Val helloTwoArgs(Val arg1, Val arg2) {
				return Val.TRUE;
			}

			@Function
			public Val helloThreeArgs(Val arg1, Val arg2, Val arg3) {
				throw new PolicyEvaluationException();
			}

		}
		var context = new AnnotationFunctionContext(new TestLib());
		var actualFullyQualified = context.getAllFullyQualifiedFunctions();
		assertThat(actualFullyQualified,
				containsInAnyOrder("test.helloThreeArgs", "test.helloVarArgs", "test.helloTwoArgs", "test.hello"));

		var actualTemplates = context.getCodeTemplates();
		actualTemplates = context.getCodeTemplates();
		assertThat(actualTemplates, containsInAnyOrder("test.hello()", "test.helloThreeArgs(arg1, arg2, arg3)",
				"test.helloTwoArgs(arg1, arg2)", "test.helloVarArgs(aVarArgs...)"));
		actualTemplates = context.getCodeTemplates();
		assertThat(actualTemplates, containsInAnyOrder("test.hello()", "test.helloThreeArgs(arg1, arg2, arg3)",
				"test.helloTwoArgs(arg1, arg2)", "test.helloVarArgs(aVarArgs...)"));
	}

	@Test
	void documentationIsAddedToTheLibrary() throws InitializationException {
		AnnotationFunctionContext context = new AnnotationFunctionContext(new MockLibrary());
		var templates = context.getDocumentedCodeTemplates(); 
		assertThat(templates, hasEntry(MockLibrary.LIBRARY_NAME, MockLibrary.LIBRARY_DOC));
	}


	@FunctionLibrary(name = MockLibrary.LIBRARY_NAME, description = MockLibrary.LIBRARY_DOC)
	public static class MockLibrary {

		public static final String FUNCTION_DOC = "docs for helloTest";

		public static final String FUNCTION_NAME = "helloTest";

		public static final Val RETURN_VALUE = Val.of("HELLO TEST");

		public static final String LIBRARY_NAME = "test.lib";

		public static final String LIBRARY_DOC = "docs of my lib";

		@Function(name = FUNCTION_NAME, docs = FUNCTION_DOC)
		public static Val helloTest() {
			return RETURN_VALUE;
		}

		@Function
		public static Val helloVarArgs(Val... args) {
			return RETURN_VALUE;
		}

		@Function
		public static Val helloTwoArgs(Val arg1, Val arg2) {
			return RETURN_VALUE;
		}

		@Function
		public static Val helloFailure(Val arg1, Val arg2, Val arg3) {
			throw new PolicyEvaluationException();
		}

	}

	@FunctionLibrary(name = "validate")
	public static class ValidationLibrary {

		@Function
		public static Val fixed(@Text Val arg) {
			return Val.UNDEFINED;
		}

		@Function
		public static Val varArgs(@Text Val... args) {
			return Val.UNDEFINED;
		}

	}

	@FunctionLibrary
	@NoArgsConstructor
	public static class FunctionLibraryWithoutName {

	}

	@FunctionLibrary
	@NoArgsConstructor
	public static class BadParameterTypeFunctionLibrary {

		@Function
		public Val fun(String param) {
			return Val.UNDEFINED;
		}

	}

	@FunctionLibrary
	@NoArgsConstructor
	public static class BadParameterTypeFunctionLibraryVarArgs {

		@Function
		public Val fun(String... param) {
			return Val.UNDEFINED;
		}

	}

	@FunctionLibrary
	@NoArgsConstructor
	public static class BadReturnTypeFunctionLibrary {

		@Function
		public String fun(Val param) {
			return param.toString();
		}

	}

}
