package io.sapl.grammar.sapl.impl.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.eclipse.emf.common.util.EList;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.variables.VariableContext;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

/**
 * Some helper methods for unit tests.
 */
@UtilityClass
public class MockUtil {
	private static SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;

	public void mockPolicyTargetExpressionContainerExpression(Expression expression) {
		var policy = FACTORY.createPolicy();
		var targetExpressionFeature = policy.eClass().getEStructuralFeature("targetExpression");
		policy.eSet(targetExpressionFeature, expression);
	}

	public void mockPolicySetTargetExpressionContainerExpression(Expression expression) {
		var policySet = FACTORY.createPolicySet();
		var targetExpressionFeature = policySet.eClass().getEStructuralFeature("targetExpression");
		policySet.eSet(targetExpressionFeature, expression);
	}

	public EvaluationContext mockEvaluationContext() {
		var ctx = mock(EvaluationContext.class);
		var functionCtx = mock(FunctionContext.class);
		when(functionCtx.evaluate(eq("mock.nil"), any())).thenReturn(Val.NULL);
		when(functionCtx.evaluate(eq("mock.emptyString"), any())).thenReturn(Val.of(""));
		when(functionCtx.evaluate(eq("mock.error"), any())).thenReturn(Val.error("Mocked error in function."));
		when(functionCtx.evaluate(eq("mock.exception"), any()))
				.thenThrow(new RuntimeException("Mocked exception in function."));
		when(functionCtx.evaluate(eq("mock.parameters"), any())).thenAnswer(new Answer<Val>() {
			@Override
			public Val answer(InvocationOnMock invocation) {
				var array = Val.JSON.arrayNode();
				for (var i = 1; i < invocation.getArguments().length; i++) {
					var val = (Val) invocation.getArguments()[i];
					if (val.isDefined()) {
						array.add(val.get());
					}
				}
				return Val.of(array);
			}
		});
		when(ctx.getFunctionCtx()).thenReturn(functionCtx);
		var attributeCtx = mock(AttributeContext.class);
		when(attributeCtx.evaluate(eq("test.nilflux"), any(), any(), any())).thenReturn(Flux.just(Val.NULL));
		when(attributeCtx.evaluate(eq("test.numbers"), any(), any(), any()))
				.thenReturn(Flux.just(Val.of(0), Val.of(1), Val.of(2), Val.of(3), Val.of(4), Val.of(5)));
		when(ctx.getAttributeCtx()).thenReturn(attributeCtx);
		var imports = new HashMap<String, String>();
		imports.put("nil", "mock.nil");
		imports.put("error", "mock.error");
		imports.put("emptyString", "mock.emptyString");
		imports.put("exception", "mock.exception");
		imports.put("parameters", "mock.parameters");
		imports.put("nilflux", "test.nilflux");
		imports.put("numbers", "test.numbers");
		when(ctx.getImports()).thenReturn(imports);
		var variableCtx = mock(VariableContext.class);
		when(variableCtx.get(any())).thenReturn(Val.UNDEFINED);
		when(variableCtx.get("nullVariable")).thenReturn(Val.NULL);
		when(ctx.getVariableCtx()).thenReturn(variableCtx);

		return ctx;
	}

	@SuppressWarnings("unchecked")
	public void mockPolicyTargetExpressionContainerExpressionForAttributeFinderStep(AttributeFinderStep expression) {
		var basicIdentifier = FACTORY.createBasicIdentifier();
		mockPolicyTargetExpressionContainerExpression(basicIdentifier);
		var stepsFeature = basicIdentifier.eClass().getEStructuralFeature("steps");
		var stepsInstance = (EList<Object>) basicIdentifier.eGet(stepsFeature, true);
		stepsInstance.add(expression);
	}

	@SuppressWarnings("unchecked")
	public void mockPolicySetTargetExpressionContainerExpressionForAttributeFinderStep(AttributeFinderStep expression) {
		var basicIdentifier = FACTORY.createBasicIdentifier();
		mockPolicySetTargetExpressionContainerExpression(basicIdentifier);
		var stepsFeature = basicIdentifier.eClass().getEStructuralFeature("steps");
		var stepsInstance = (EList<Object>) basicIdentifier.eGet(stepsFeature, true);
		stepsInstance.add(expression);
	}

}
