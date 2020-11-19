package io.sapl.grammar.sapl.impl.util;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.EList;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.Text;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.SimpleFunctionLibrary;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import reactor.core.publisher.Flux;

/**
 * Some helper methods for unit tests.
 */
public class MockUtil {
	private static SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;

	public static void mockPolicyTargetExpressionContainerExpression(Expression expression) {
		var policy = FACTORY.createPolicy();
		var targetExpressionFeature = policy.eClass().getEStructuralFeature("targetExpression");
		policy.eSet(targetExpressionFeature, expression);
	}

	public static void mockPolicySetTargetExpressionContainerExpression(Expression expression) {
		var policySet = FACTORY.createPolicySet();
		var targetExpressionFeature = policySet.eClass().getEStructuralFeature("targetExpression");
		policySet.eSet(targetExpressionFeature, expression);
	}

	public static EvaluationContext constructTestEnvironmentEvaluationContext() {

		var attributeCtx = new AnnotationAttributeContext();
		attributeCtx.loadPolicyInformationPoint(new TestPolicyInformationPoint());

		var functionCtx = new AnnotationFunctionContext();
		functionCtx.loadLibrary(new SimpleFunctionLibrary());
		functionCtx.loadLibrary(new FilterFunctionLibrary());
		functionCtx.loadLibrary(new TestFunctionLibrary());

		var variables = new HashMap<String, JsonNode>(1);
		variables.put("nullVariable", Val.JSON.nullNode());

		var evaluationCtx = new EvaluationContext(attributeCtx, functionCtx, variables);
		var imports = new HashMap<String, String>();

		evaluationCtx = evaluationCtx.withImports(imports);

		return evaluationCtx;
	}

	@FunctionLibrary(name = "mock")
	public static class TestFunctionLibrary {
		@Function
		public Val nil(Val... parameters) {
			return Val.NULL;
		}

		@Function
		public Val emptyString(Val... parameters) {
			return Val.of("");
		}

		@Function
		public Val error(Val... parameters) {
			return Val.error("INTENTIONALLY CREATED TEST ERROR");
		}

		@Function
		public Val exception(Val... parameters) {
			throw new RuntimeException("INTENTIONALLY THROWN TEST EXCEPTION");
		}

		@Function
		public Val parameters(Val... parameters) {
			var array = Val.JSON.arrayNode();
			for (var param : parameters)
				array.add(param.get());
			return Val.of(array);
		}
	}

	@PolicyInformationPoint(name = "test")
	public static class TestPolicyInformationPoint {

		@Attribute
		public Flux<Val> nilflux(Val value, Map<String, JsonNode> variables) {
			return Flux.just(Val.NULL);
		}

		@Attribute
		public Flux<Val> numbers(Val value, Map<String, JsonNode> variables) {
			return Flux.just(Val.of(0), Val.of(1), Val.of(2), Val.of(3), Val.of(4), Val.of(5));
		}

		@Attribute
		public Flux<Val> numbersWithError(@Text Val value, Map<String, JsonNode> variables) {
			return Flux.just(Val.of(0), Val.of(1), Val.error("INTENTIONAL ERROR IN SEQUENCE"), Val.of(3), Val.of(4),
					Val.of(5));
		}

	}

	@SuppressWarnings("unchecked")
	public static void mockPolicyTargetExpressionContainerExpressionForAttributeFinderStep(
			AttributeFinderStep expression) {
		var basicIdentifier = FACTORY.createBasicIdentifier();
		mockPolicyTargetExpressionContainerExpression(basicIdentifier);
		var stepsFeature = basicIdentifier.eClass().getEStructuralFeature("steps");
		var stepsInstance = (EList<Object>) basicIdentifier.eGet(stepsFeature, true);
		stepsInstance.add(expression);
	}

	@SuppressWarnings("unchecked")
	public static void mockPolicySetTargetExpressionContainerExpressionForAttributeFinderStep(
			AttributeFinderStep expression) {
		var basicIdentifier = FACTORY.createBasicIdentifier();
		mockPolicySetTargetExpressionContainerExpression(basicIdentifier);
		var stepsFeature = basicIdentifier.eClass().getEStructuralFeature("steps");
		var stepsInstance = (EList<Object>) basicIdentifier.eGet(stepsFeature, true);
		stepsInstance.add(expression);
	}

}
