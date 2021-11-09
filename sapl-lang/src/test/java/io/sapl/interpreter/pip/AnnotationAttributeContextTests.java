package io.sapl.interpreter.pip;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.Bool;
import io.sapl.api.validation.Text;
import io.sapl.grammar.sapl.impl.util.ParserUtil;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class AnnotationAttributeContextTests {

	@Test
	public void when_classHasNoAnnotation_fail() {
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(
				"I am an instance of a class without @PolicyInformationPoint annotation"));
	}

	@Test
	public void when_classHasNoAttributesDeclared_fail() {
		@PolicyInformationPoint
		class PIP {

		}
		var pip = new PIP();
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(pip));
	}

	@Test
	public void when_pipWithSameNameExists_fail() {
		@PolicyInformationPoint(name = "somePip")
		class PIP {
			@Attribute
			public Flux<Val> x() {
				return null;
			}
		}

		var pip = new PIP();
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(pip, pip));
	}

	@Test
	public void when_firstParameterOfAttributeIllegal_fail() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(String x) {
				return null;
			}
		}

		var pip = new PIP();
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(pip));
	}

	@Test
	public void when_returnTypeIllegal_fail() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public void x() {
			}
		}

		var pip = new PIP();
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(pip));
	}

	@Test
	public void when_returnTypeIllegalFluxType_fail() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<String> x() {
				return null;
			}
		}

		var pip = new PIP();
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(pip));
	}

	@Test
	public void when_returnTypeIllegalGenericVal_fail() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public List<Val> x() {
				return null;
			}
		}

		var pip = new PIP();
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(pip));
	}

	@Test
	public void when_firstAndOnlyParameterVal_loadSuccessful() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(Val leftHand) {
				return null;
			}
		}

		var pip = new PIP();
		var ctx = new AnnotationAttributeContext();
		assertDoesNotThrow(() -> ctx.loadPolicyInformationPoint(pip));
		assertThat(ctx.getAvailableLibraries().contains("PIP"), is(true));
		assertThat(ctx.providedFunctionsOfLibrary("PIP").contains("x"), is(true));
		assertThat(ctx.isProvidedFunction("PIP.x"), is(true));
		assertThat(new ArrayList<>(ctx.getDocumentation()).get(0).getName(), is("PIP"));
	}

	@Test
	public void when_noPip_providedIsEmpty() {
		var ctx = new AnnotationAttributeContext();
		assertThat(ctx.providedFunctionsOfLibrary("PIP").isEmpty(), is(true));
		assertThat(ctx.getDocumentation().isEmpty(), is(true));
	}

	@Test
	public void when_firstAndOnlyParameterVariablesMap_loadSuccessful() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(Map<String, JsonNode> variables) {
				return null;
			}
		}

		var pip = new PIP();
		var ctx = new AnnotationAttributeContext();
		assertDoesNotThrow(() -> ctx.loadPolicyInformationPoint(pip));
	}

	@Test
	public void when_firstAndOnlyParameterMapWithBadKeyType_fail() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(Map<Long, JsonNode> variables) {
				return null;
			}
		}

		var pip = new PIP();
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(pip));
	}

	@Test
	public void when_firstAndOnlyParameterMapWithBadValueType_fail() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(Map<String, String> variables) {
				return null;
			}
		}

		var pip = new PIP();
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(pip));
	}

	@Test
	public void when_firstAndOnlyParameterFluxOfVal_loadSuccessful() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(Flux<Val> firstParameter) {
				return null;
			}
		}

		var pip = new PIP();
		var ctx = new AnnotationAttributeContext();
		assertDoesNotThrow(() -> ctx.loadPolicyInformationPoint(pip));
	}

	@Test
	public void when_firstAndOnlyParameterNotAFlux_fail() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(Object firstParameter) {
				return null;
			}
		}

		var pip = new PIP();
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(pip));
	}

	@Test
	public void when_firstAndOnlyParameterFluxWithBadContentsType_fail() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(Flux<String> firstParameter) {
				return null;
			}
		}

		var pip = new PIP();
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(pip));
	}

	@Test
	public void when_someParamBAdType_fail() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(Flux<Val> firstParameter, String x) {
				return null;
			}
		}

		var pip = new PIP();
		assertThrows(InitializationException.class, () -> new AnnotationAttributeContext(pip));
	}

	@Test
	public void when_firstAndOnlyParameterIsVarArgsFluxOfVal_loadSuccessful() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			@SuppressWarnings("unchecked")
			public Flux<Val> x(Flux<Val>... varArgsParams) {
				return null;
			}
		}

		var pip = new PIP();
		var ctx = new AnnotationAttributeContext();
		assertDoesNotThrow(() -> ctx.loadPolicyInformationPoint(pip));
	}

	@Test
	public void when_firstAndOnlyParameterIsArrayOfFluxOfVal_loadSuccessful() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(Flux<Val>[] varArgsParams) {
				return null;
			}
		}

		var pip = new PIP();
		var ctx = new AnnotationAttributeContext();
		assertDoesNotThrow(() -> ctx.loadPolicyInformationPoint(pip));
	}

	@Test
	public void when_arrayFollowedBYSomething_failImport() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(Flux<Val>[] varArgsParams, String iAmTooMuchToHandle) {
				return null;
			}
		}

		var pip = new PIP();
		var ctx = new AnnotationAttributeContext();
		assertThrows(InitializationException.class, () -> ctx.loadPolicyInformationPoint(pip));
	}

	@Test
	public void when_firstAndOnlyParameterIsVarArgsString_fail() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(String... varArgsParams) {
				return null;
			}
		}

		var pip = new PIP();
		var ctx = new AnnotationAttributeContext();
		assertThrows(InitializationException.class, () -> ctx.loadPolicyInformationPoint(pip));
	}

	@Test
	public void when_firstAndOnlyParameterIsVarFluxNoGeneric_fail() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(@SuppressWarnings("rawtypes") Flux... varArgsParams) {
				return null;
			}
		}

		var pip = new PIP();
		var ctx = new AnnotationAttributeContext();
		assertThrows(InitializationException.class, () -> ctx.loadPolicyInformationPoint(pip));
	}

	@Test
	public void when_firstAndOnlyParameterIsVarWringGenericWithVal_fail() {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(@SuppressWarnings("unchecked") List<Val>... varArgsParams) {
				return null;
			}
		}

		var pip = new PIP();
		var ctx = new AnnotationAttributeContext();
		assertThrows(InitializationException.class, () -> ctx.loadPolicyInformationPoint(pip));
	}

	@Test
	public void when_differentNames_noCollision() throws InitializationException {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(Flux<Val> param) {
				return null;
			}

			@Attribute
			public Flux<Val> y(Flux<Val> parameter) {
				return null;
			}
		}

		var pip = new PIP();
		var ctx = new AnnotationAttributeContext();
		assertDoesNotThrow(() -> ctx.loadPolicyInformationPoint(pip));
	}


	@Test
	public void when_envAndNonEnv_noCollision() throws InitializationException {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x() {
				return null;
			}

			@Attribute
			public Flux<Val> x(Val leftHand) {
				return null;
			}
		}

		var pip = new PIP();
		var ctx = new AnnotationAttributeContext();
		assertDoesNotThrow(() -> ctx.loadPolicyInformationPoint(pip));
	}
	
	@Test
	public void when_varargAndNonVarArg_noCollision() throws InitializationException {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(Flux<Val> a) {
				return null;
			}

			@Attribute
			public Flux<Val> x(@SuppressWarnings("unchecked") Flux<Val>... a) {
				return null;
			}
		}

		var pip = new PIP();
		var ctx = new AnnotationAttributeContext();
		assertDoesNotThrow(() -> ctx.loadPolicyInformationPoint(pip));
	}
	
	@Test
	public void when_twoVarArg_collision() throws InitializationException {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(Flux<Val>[] a) {
				return null;
			}

			@Attribute(name="x")
			public Flux<Val> y(@SuppressWarnings("unchecked") Flux<Val>... a) {
				return null;
			}
		}

		var pip = new PIP();
		var ctx = new AnnotationAttributeContext();
		assertThrows(InitializationException.class, () -> ctx.loadPolicyInformationPoint(pip));
	}

	@Test
	public void when_sameNumberOFParams_collision() throws InitializationException {
		@PolicyInformationPoint
		class PIP {
			@Attribute
			public Flux<Val> x(Flux<Val> a) {
				return null;
			}

			@Attribute(name="x")
			public Flux<Val> y(Flux<Val> b) {
				return null;
			}
		}

		var pip = new PIP();
		var ctx = new AnnotationAttributeContext();
		assertThrows(InitializationException.class, () -> ctx.loadPolicyInformationPoint(pip));
	}

	@Test
	public void when_evaluateUnknownAttribute_fails() throws InitializationException, IOException {
		var attributeCtx = new AnnotationAttributeContext();
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("<test.envAttribute(\"param1\",\"param2\")>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void when_varArgsWithVariablesEnvironmentAttribute_evaluates() throws InitializationException, IOException {
		@PolicyInformationPoint(name = "test")
		class PIP {
			@Attribute
			public Flux<Val> envAttribute(Map<String, JsonNode> variables,
					@SuppressWarnings("unchecked") @Text Flux<Val>... varArgsParams) {
				return varArgsParams[1];
			}
		}

		var pip = new PIP();
		var attributeCtx = new AnnotationAttributeContext(pip);
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("<test.envAttribute(\"param1\",\"param2\")>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNext(Val.of("param2")).verifyComplete();
	}

	@Test
	public void when_varArgsWithVariablesAttribute_evaluates() throws InitializationException, IOException {
		@PolicyInformationPoint(name = "test")
		class PIP {
			@Attribute
			public Flux<Val> envAttribute(Val leftHand, Map<String, JsonNode> variables,
					@SuppressWarnings("unchecked") @Text Flux<Val>... varArgsParams) {
				return varArgsParams[1];
			}
		}

		var pip = new PIP();
		var attributeCtx = new AnnotationAttributeContext(pip);
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("\"\".<test.envAttribute(\"param1\",\"param2\")>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNext(Val.of("param2")).verifyComplete();
	}

	@Test
	public void when_varArgsWithVariablesAndTwoAttributes_evaluates() throws InitializationException, IOException {
		@PolicyInformationPoint(name = "test")
		class PIP {
			@Attribute
			public Flux<Val> envAttribute(Val leftHand, Map<String, JsonNode> variables, @Text Flux<Val> param1,
					@Text Flux<Val> param2) {
				return param2;
			}
		}

		var pip = new PIP();
		var attributeCtx = new AnnotationAttributeContext(pip);
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("\"\".<test.envAttribute(\"param1\",\"param2\")>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNext(Val.of("param2")).verifyComplete();
	}

	@Test
	public void when_varArgsNoVariablesEnvironmentAttribute_evaluates() throws InitializationException, IOException {
		@PolicyInformationPoint(name = "test")
		class PIP {
			@Attribute
			public Flux<Val> envAttribute(@SuppressWarnings("unchecked") @Text Flux<Val>... varArgsParams) {
				return varArgsParams[1];
			}
		}

		var pip = new PIP();
		var attributeCtx = new AnnotationAttributeContext(pip);
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("<test.envAttribute(\"param1\",\"param2\")>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNext(Val.of("param2")).verifyComplete();
	}

	@Test
	public void when_varsAndParamEnvironmentAttribute_evaluates() throws InitializationException, IOException {
		@PolicyInformationPoint(name = "test")
		class PIP {
			@Attribute
			public Flux<Val> envAttribute(Map<String, JsonNode> variables, @Text Flux<Val> param1) {
				return param1;
			}
		}

		var pip = new PIP();
		var attributeCtx = new AnnotationAttributeContext(pip);
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("<test.envAttribute(\"param1\")>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNext(Val.of("param1")).verifyComplete();
	}

	@Test
	public void when_varArgsAndTwoParamEnvironmentAttribute_evaluatesExactParameterMatch()
			throws InitializationException, IOException {
		@PolicyInformationPoint(name = "test")
		class PIP {
			@Attribute
			public Flux<Val> envAttribute(Map<String, JsonNode> variables, @Text Flux<Val> param1,
					@Text Flux<Val> param2) {
				return param1;
			}

			@Attribute
			public Flux<Val> envAttribute(Map<String, JsonNode> variables,
					@SuppressWarnings("unchecked") @Text Flux<Val>... varArgsParams) {
				return varArgsParams[1];
			}
		}

		var pip = new PIP();
		var attributeCtx = new AnnotationAttributeContext(pip);
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("<test.envAttribute(\"param1\",\"param2\")>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNext(Val.of("param1")).verifyComplete();
	}

	@Test
	public void when_varArgsEnvironmentAttribute_calledWithNoParams_evalsVarArgsWithEmptyParamArray()
			throws InitializationException, IOException {
		@PolicyInformationPoint(name = "test")
		class PIP {
			@Attribute
			public Flux<Val> envAttribute(Map<String, JsonNode> variables,
					@SuppressWarnings("unchecked") @Text Flux<Val>... varArgsParams) {
				return Flux.just(Val.of(varArgsParams.length));
			}
		}

		var pip = new PIP();
		var attributeCtx = new AnnotationAttributeContext(pip);
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("<test.envAttribute>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNext(Val.of(0)).verifyComplete();
	}

	@Test
	public void when_noArgsEnvironmentAttribute_called_evals() throws InitializationException, IOException {
		@PolicyInformationPoint(name = "test")
		class PIP {
			@Attribute
			public Flux<Val> envAttribute() {
				return Flux.just(Val.of("OK"));
			}
		}

		var pip = new PIP();
		var attributeCtx = new AnnotationAttributeContext(pip);
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("<test.envAttribute>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNext(Val.of("OK")).verifyComplete();
	}

	@Test
	public void when_noArgsEnvironmentAttribute_calledAndFails_evalsToError()
			throws InitializationException, IOException {
		@PolicyInformationPoint(name = "test")
		class PIP {
			@Attribute
			public Flux<Val> envAttribute() {
				throw new IllegalStateException("ERROR");
			}
		}

		var pip = new PIP();
		var attributeCtx = new AnnotationAttributeContext(pip);
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("<test.envAttribute>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void when_noArgsAttribute_calledAndFails_evalsToError() throws InitializationException, IOException {
		@PolicyInformationPoint(name = "test")
		class PIP {
			@Attribute
			public Flux<Val> attribute(Val leftHand) {
				throw new IllegalStateException("ERROR");
			}
		}

		var pip = new PIP();
		var attributeCtx = new AnnotationAttributeContext(pip);
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("\"\".<test.attribute>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void when_noArgsAttribute_called_evals() throws InitializationException, IOException {
		@PolicyInformationPoint(name = "test")
		class PIP {
			@Attribute
			public Flux<Val> attribute(Val leftHand) {
				return Flux.just(leftHand);
			}
		}

		var pip = new PIP();
		var attributeCtx = new AnnotationAttributeContext(pip);
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("\"\".<test.attribute>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNext(Val.of("")).verifyComplete();
	}

	@Test
	public void when_unkownAttribute_called_evalsToError() throws InitializationException, IOException {
		var attributeCtx = new AnnotationAttributeContext();
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("\"\".<test.envAttribute>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void when_twoParamEnvironmentAttribute_calledWithOneParam_evaluatesToError()
			throws InitializationException, IOException {
		@PolicyInformationPoint(name = "test")
		class PIP {
			@Attribute
			public Flux<Val> envAttribute(Map<String, JsonNode> variables, @Text Flux<Val> param1,
					@Text Flux<Val> param2) {
				return param1;
			}
		}

		var pip = new PIP();
		var attributeCtx = new AnnotationAttributeContext(pip);
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("<test.envAttribute(\"param1\")>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void when_varArgsWithVariablesEnvironmentAttributeAndBadParamType_evaluatesToError()
			throws InitializationException, IOException {
		@PolicyInformationPoint(name = "test")
		class PIP {
			@Attribute
			public Flux<Val> envAttribute(Map<String, JsonNode> variables,
					@SuppressWarnings("unchecked") @Bool Flux<Val>... varArgsParams) {
				return varArgsParams[1];
			}
		}

		var pip = new PIP();
		var attributeCtx = new AnnotationAttributeContext(pip);
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("<test.envAttribute(\"param1\",\"param2\")>");
		StepVerifier.create(expression.evaluate(evalCtx, Val.UNDEFINED)).expectNextMatches(Val::isError)
				.verifyComplete();
	}

	@Test
	public void when_environmentAttributeButOnlyNonEnvAttributePresent_fail()
			throws InitializationException, IOException {
		@PolicyInformationPoint(name = "test")
		class PIP {
			@Attribute
			public Flux<Val> attribute(Val leftHand, Map<String, JsonNode> variables,
					@SuppressWarnings("unchecked") @Text Flux<Val>... varArgsParams) {
				return varArgsParams[1];
			}
		}

		var pip = new PIP();
		var attributeCtx = new AnnotationAttributeContext(pip);
		var variables = Map.of("key1", (JsonNode) Val.JSON.textNode("valueOfKey"));
		var evalCtx = constructContext(attributeCtx, variables);
		var expression = ParserUtil.expression("<test.attribute(\"param1\",\"param2\")>");
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
