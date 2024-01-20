/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.testutil;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.EList;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.SimpleFunctionLibrary;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

public class MockUtil {

    private static final SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;

    public static void mockPolicyTargetExpressionContainerExpression(Expression expression) {
        var policy                  = FACTORY.createPolicy();
        var targetExpressionFeature = policy.eClass().getEStructuralFeature("targetExpression");
        policy.eSet(targetExpressionFeature, expression);
    }

    public static void mockPolicySetTargetExpressionContainerExpression(Expression expression) {
        var policySet               = FACTORY.createPolicySet();
        var targetExpressionFeature = policySet.eClass().getEStructuralFeature("targetExpression");
        policySet.eSet(targetExpressionFeature, expression);
    }

    public static Context setUpAuthorizationContext(Context ctx) {
        var attributeCtx = new AnnotationAttributeContext();
        var functionCtx  = new AnnotationFunctionContext();
        try {
            attributeCtx.loadPolicyInformationPoint(new TestPolicyInformationPoint());
            functionCtx.loadLibrary(SimpleFunctionLibrary.class);
            functionCtx.loadLibrary(FilterFunctionLibrary.class);
            functionCtx.loadLibrary(TestFunctionLibrary.class);
        } catch (InitializationException e) {
            fail("The loading of function libraries for the test environment failed: " + e.getMessage());
        }

        ctx = AuthorizationContext.setAttributeContext(ctx, attributeCtx);
        ctx = AuthorizationContext.setFunctionContext(ctx, functionCtx);
        ctx = AuthorizationContext.setVariable(ctx, "nullVariable", Val.NULL);
        ctx = AuthorizationContext.setImports(ctx, new HashMap<>());

        return ctx;
    }

    public static Context setUpAuthorizationContext(Context ctx, AuthorizationSubscription authzSubscription) {
        ctx = setUpAuthorizationContext(ctx);
        ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription);
        return ctx;
    }

    @UtilityClass
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

        @EnvironmentAttribute
        public Flux<Val> nilflux(Map<String, JsonNode> variables) {
            return Flux.just(Val.NULL);
        }

        @EnvironmentAttribute
        public Flux<Val> numbers(Map<String, JsonNode> variables) {
            return Flux.just(Val.of(0), Val.of(1), Val.of(2), Val.of(3), Val.of(4), Val.of(5));
        }

        @Attribute
        public Flux<Val> numbers(Val leftHand, Map<String, JsonNode> variables) {
            return Flux.just(Val.of(0), Val.of(1), Val.of(2), Val.of(3), Val.of(4), Val.of(5));
        }

        @EnvironmentAttribute
        public Flux<Val> numbersWithError(Map<String, JsonNode> variables) {
            return Flux.just(Val.of(0), Val.of(1), Val.error("INTENTIONAL ERROR IN SEQUENCE"), Val.of(3), Val.of(4),
                    Val.of(5));
        }

    }

    @SuppressWarnings("unchecked")
    public static void mockPolicyTargetExpressionContainerExpressionForAttributeFinderStep(
            AttributeFinderStep expression) {
        var basicIdentifier = FACTORY.createBasicIdentifier();
        mockPolicyTargetExpressionContainerExpression(basicIdentifier);
        var stepsFeature  = basicIdentifier.eClass().getEStructuralFeature("steps");
        var stepsInstance = (EList<Object>) basicIdentifier.eGet(stepsFeature, true);
        stepsInstance.add(expression);
    }

    @SuppressWarnings("unchecked")
    public static void mockPolicySetTargetExpressionContainerExpressionForAttributeFinderStep(
            AttributeFinderStep expression) {
        var basicIdentifier = FACTORY.createBasicIdentifier();
        mockPolicySetTargetExpressionContainerExpression(basicIdentifier);
        var stepsFeature  = basicIdentifier.eClass().getEStructuralFeature("steps");
        var stepsInstance = (EList<Object>) basicIdentifier.eGet(stepsFeature, true);
        stepsInstance.add(expression);
    }

}
