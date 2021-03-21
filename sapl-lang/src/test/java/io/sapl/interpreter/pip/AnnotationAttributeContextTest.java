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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.grammar.sapl.impl.util.ParserUtil;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class AnnotationAttributeContextTest {

	@Test
	void registerFinder() throws InitializationException {
		var pip = new TestPIP();
		var attributeCtx = new AnnotationAttributeContext();
		attributeCtx.loadPolicyInformationPoint(pip);
		assertThat(attributeCtx.isProvidedFunction("sapl.pip.test.echo"), is(true));
	}

	@Test
	void registerFinderAtConstructorTime() throws InitializationException {
		var attributeCtx = new AnnotationAttributeContext(new TestPIP());
		assertThat(attributeCtx.isProvidedFunction("sapl.pip.test.echo"), is(true));
	}

	@Test
	void registerPOJONoAnnotationFails() {
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(""));
	}

	@Test
	void documentationContainsDeclaredNameOfPIP() throws InitializationException {
		var attributeCtx = new AnnotationAttributeContext(new TestPIP());
		assertThat(new ArrayList<>(attributeCtx.getDocumentation()).get(0).getName(), is(TestPIP.NAME));
	}

	@Test
	void failToLoadPIPWithourAnnotation() {
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(""));
	}

	@Test
	void documentationContainsClassNameIfNotDeclaredByPIP() throws InitializationException {
		var attributeCtx = new AnnotationAttributeContext(new NoNamePIP());
		assertThat(new ArrayList<>(attributeCtx.getDocumentation()).get(0).getName(), is("NoNamePIP"));
	}

	@PolicyInformationPoint
	public static class NoNamePIP {
	}

	@Test
	void documentationContainsDeclaredDescriptionOfPIP() throws InitializationException {
		var attributeCtx = new AnnotationAttributeContext(new TestPIP());
		assertThat(new ArrayList<>(attributeCtx.getDocumentation()).get(0).getDescription(), is(TestPIP.DESCRIPTION));
	}

	@Test
	void attributeGetsAnnotatedName() throws InitializationException {
		@PolicyInformationPoint
		class AttributeNameTestPIP {
			@Attribute(name = "theName")
			Flux<Val> notTheName(Val leftHand, Map<String, JsonNode> variables) {
				return Val.fluxOfUndefined();
			}
		}
		var attributeCtx = new AnnotationAttributeContext(new AttributeNameTestPIP());
		assertAll(() -> assertThat(attributeCtx.isProvidedFunction("AttributeNameTestPIP.theName"), is(true)),
				() -> assertThat(attributeCtx.providedFunctionsOfLibrary("AttributeNameTestPIP"), contains("theName")),
				() -> assertThat(attributeCtx.providedFunctionsOfLibrary("unknown"), empty()));
	}

	@Test
	void attributeGetsAnnotatedDescription() throws InitializationException {
		@PolicyInformationPoint
		class AttributeNameTestPIP {
			@Attribute(docs = "doc")
			Flux<Val> theName(Val leftHand, Map<String, JsonNode> variables) {
				return Val.fluxOfUndefined();
			}
		}
		var attributeCtx = new AnnotationAttributeContext(new AttributeNameTestPIP());
		assertThat(new ArrayList<>(attributeCtx.getDocumentation()).get(0).documentation.get("theName"), is("doc"));
	}

	@Test
	void attributeGetsMethodWhenNotAnnotatedName() throws InitializationException {
		@PolicyInformationPoint
		class AttributeNoNameTestPIP {
			@Attribute
			Flux<Val> theName(Val leftHand, Map<String, JsonNode> variables) {
				return Val.fluxOfUndefined();
			}
		}
		var attributeCtx = new AnnotationAttributeContext(new AttributeNoNameTestPIP());
		assertThat(attributeCtx.isProvidedFunction("AttributeNoNameTestPIP.theName"), is(true));
	}

	@Test
	void failToLoadPIPAttributeWithZeroParameters() {
		@PolicyInformationPoint
		class AttributeZeroParameterTestPIP {
			@Attribute
			Flux<Val> theName() {
				return Val.fluxOfUndefined();
			}
		}
		assertThrows(InitializationException.class,
				() -> new AnnotationAttributeContext(new AttributeZeroParameterTestPIP()));
	}

	@Test
	void failToLoadPIPAttributeWithOneParameter() {
		@PolicyInformationPoint
		class AttributeOneParameterTestPIP {
			@Attribute
			Flux<Val> theName(Val leftHand) {
				return Val.fluxOfUndefined();
			}
		}
		assertThrows(InitializationException.class,
				() -> new AnnotationAttributeContext(new AttributeOneParameterTestPIP()));
	}

	@Test
	void failToLoadPIPAttributeWithWrongParamTypeForMap() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			Flux<Val> theName(Val leftHand, Val hereShouldBeTheMap) {
				return Val.fluxOfUndefined();
			}
		}
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(new PIP()));
	}

	@Test
	void failToLoadPIPAttributeWithWrongParamTypeForMapWithRegardsToGenericsKey() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			Flux<Val> theName(Val leftHand, Map<Boolean, JsonNode> variable) {
				return Val.fluxOfUndefined();
			}
		}
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(new PIP()));
	}

	@Test
	void failToLoadPIPAttributeWithWrongParamTypeForMapWithRegardsToGenericsValue() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			Flux<Val> theName(Val leftHand, Map<String, Integer> variable) {
				return Val.fluxOfUndefined();
			}
		}
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(new PIP()));
	}

	@Test
	void failToLoadPIPAttributeWithWrongParamTypeForVal() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			Flux<Val> theName(String shouldBeAVal, Map<String, JsonNode> variables) {
				return Val.fluxOfUndefined();
			}
		}
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(new PIP()));
	}

	@Test
	void failToLoadPIPAttributeWithWrongParamTypeRightHandParameters() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			Flux<Val> theName(Val val, Map<String, JsonNode> variables, String param) {
				return Val.fluxOfUndefined();
			}
		}
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(new PIP()));
	}

	@Test
	void failToLoadPIPAttributeWithWrongGenericInFluxParamTypeRightHandParameters() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			Flux<Val> theName(Val val, Map<String, JsonNode> variables, Flux<String> param) {
				return Val.fluxOfUndefined();
			}
		}
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(new PIP()));
	}

	@Test
	void failToLoadPIPAttributeWithWrongReturnType() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			String theName(Val val, Map<String, JsonNode> variables) {
				return "";
			}
		}
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(new PIP()));
	}

	@Test
	void failToLoadPIPAttributeWithWrongGenericReturnType() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			Flux<String> theName(Val val, Map<String, JsonNode> variables) {
				return Flux.just("");
			}
		}
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(new PIP()));
	}

	@Test
	void failToLoadPIPAttributeWithWrongGenericReturnType2() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			List<Val> theName(Val val, Map<String, JsonNode> variables) {
				return null;
			}
		}
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(new PIP()));
	}

	@Test
	void failToLoadPIPAttributeWithNameCollisiton() {
		@PolicyInformationPoint
		class PIP {
			@Attribute(name = "collision")
			Flux<Val> theName(Val val, Map<String, JsonNode> variables) {
				return null;
			}

			@Attribute(name = "collision")
			Flux<Val> anotherName(Val val, Map<String, JsonNode> variables) {
				return null;
			}
		}
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(new PIP()));
	}

	@Test
	void evaluateComplete() throws InitializationException, IOException {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			Flux<Val> attribute(Val leftHand, Map<String, JsonNode> variables, Flux<Val> param1, Flux<Val> param2) {
				return Flux.just(leftHand, Val.of(variables.get("key1")), param1.blockFirst(), param2.blockFirst());
			}
		}
		var attributeCtx = new AnnotationAttributeContext(new PIP());
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("\"leftHand\".<PIP.attribute(\"param1\",\"param2\")>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED))
				.expectNext(Val.of("leftHand"), Val.of("valueOfKey"), Val.of("param1"), Val.of("param2"))
				.verifyComplete();
	}

	@Test
	void evaluateEnvironment() throws InitializationException, IOException {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			Flux<Val> attribute(Val leftHand, Map<String, JsonNode> variables) {
				return Flux.just(leftHand, Val.of(variables.get("key1")));
			}
		}
		var attributeCtx = new AnnotationAttributeContext(new PIP());
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("<PIP.attribute>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNext(Val.UNDEFINED, Val.of("valueOfKey"))
				.verifyComplete();
	}

	@Test
	void evaluateCatchesPolicEvaluationException() throws InitializationException, IOException {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			Flux<Val> attribute(Val leftHand, Map<String, JsonNode> variables) {
				throw new PolicyEvaluationException("error");
			}
		}
		var attributeCtx = new AnnotationAttributeContext(new PIP());
		var variables = new HashMap<String, JsonNode>();
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("\"leftHand\".<PIP.attribute>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	void unknownAttributeEvaluatesToError() throws InitializationException, IOException {
		@PolicyInformationPoint
		class PIP {
		}
		var attributeCtx = new AnnotationAttributeContext(new PIP());
		var variables = new HashMap<String, JsonNode>();
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("\"leftHand\".<PIP.attribute>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	public static EvaluationContext constructContext(AttributeContext attributeCtx, Map<String, JsonNode> variables) {
		var functionCtx = new AnnotationFunctionContext();
		var evaluationCtx = new EvaluationContext(attributeCtx, functionCtx, variables);
		var imports = new HashMap<String, String>();
		evaluationCtx = evaluationCtx.withImports(imports);
		return evaluationCtx;
	}

}
