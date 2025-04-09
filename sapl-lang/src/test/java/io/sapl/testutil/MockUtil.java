/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.attributes.broker.impl.AnnotationPolicyInformationPointLoader;
import io.sapl.attributes.broker.impl.CachingAttributeStreamBroker;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.grammar.sapl.impl.util.ErrorFactory;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.SimpleFunctionLibrary;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.validation.ValidatorFactory;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

public class MockUtil {

    private static final SaplFactory FACTORY = SaplFactoryImpl.eINSTANCE;

    public static void mockPolicyTargetExpressionContainerExpression(Expression expression) {
        final var policy                  = FACTORY.createPolicy();
        final var targetExpressionFeature = policy.eClass().getEStructuralFeature("targetExpression");
        policy.eSet(targetExpressionFeature, expression);
    }

    public static void mockPolicySetTargetExpressionContainerExpression(Expression expression) {
        final var policySet               = FACTORY.createPolicySet();
        final var targetExpressionFeature = policySet.eClass().getEStructuralFeature("targetExpression");
        policySet.eSet(targetExpressionFeature, expression);
    }

    public static Context setUpAuthorizationContext(Context ctx) {
        final var mapper                = new ObjectMapper();
        final var validatorFactory      = new ValidatorFactory(mapper);
        final var attributeStreamBroker = new CachingAttributeStreamBroker();
        final var pipLoader             = new AnnotationPolicyInformationPointLoader(attributeStreamBroker,
                validatorFactory);
        final var functionCtx           = new AnnotationFunctionContext();
        try {
            pipLoader.loadPolicyInformationPoint(new TestPolicyInformationPoint());
            functionCtx.loadLibrary(SimpleFunctionLibrary.class);
            functionCtx.loadLibrary(FilterFunctionLibrary.class);
            functionCtx.loadLibrary(TestFunctionLibrary.class);
        } catch (InitializationException e) {
            fail("The loading of libraries for the test environment failed: " + e.getMessage());
        }

        ctx = AuthorizationContext.setAttributeStreamBroker(ctx, attributeStreamBroker);
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
            return ErrorFactory.error("INTENTIONALLY CREATED TEST ERROR");
        }

        @Function
        public Val exception(Val... parameters) {
            throw new RuntimeException("INTENTIONALLY THROWN TEST EXCEPTION");
        }

        @Function
        public Val parameters(Val... parameters) {
            final var array = Val.JSON.arrayNode();
            for (var param : parameters)
                array.add(param.get());
            return Val.of(array);
        }

    }

    @PolicyInformationPoint(name = "test")
    public static class TestPolicyInformationPoint {

        @EnvironmentAttribute
        public Flux<Val> nilflux(Map<String, Val> variables) {
            return Flux.just(Val.NULL);
        }

        @EnvironmentAttribute
        public Flux<Val> numbers(Map<String, Val> variables) {
            return Flux.just(Val.of(0), Val.of(1), Val.of(2), Val.of(3), Val.of(4), Val.of(5));
        }

        @Attribute
        public Flux<Val> numbers(Val leftHand, Map<String, Val> variables) {
            return Flux.just(Val.of(0), Val.of(1), Val.of(2), Val.of(3), Val.of(4), Val.of(5));
        }

        @EnvironmentAttribute
        public Flux<Val> numbersWithError(Map<String, Val> variables) {
            return Flux.just(Val.of(0), Val.of(1), ErrorFactory.error("INTENTIONAL ERROR IN SEQUENCE"), Val.of(3),
                    Val.of(4), Val.of(5));
        }

    }

    @SuppressWarnings("unchecked")
    public static void mockPolicyTargetExpressionContainerExpressionForAttributeFinderStep(
            AttributeFinderStep expression) {
        final var basicIdentifier = FACTORY.createBasicIdentifier();
        mockPolicyTargetExpressionContainerExpression(basicIdentifier);
        final var stepsFeature  = basicIdentifier.eClass().getEStructuralFeature("steps");
        final var stepsInstance = (EList<Object>) basicIdentifier.eGet(stepsFeature, true);
        stepsInstance.add(expression);
    }

    @SuppressWarnings("unchecked")
    public static void mockPolicySetTargetExpressionContainerExpressionForAttributeFinderStep(
            AttributeFinderStep expression) {
        final var basicIdentifier = FACTORY.createBasicIdentifier();
        mockPolicySetTargetExpressionContainerExpression(basicIdentifier);
        final var stepsFeature  = basicIdentifier.eClass().getEStructuralFeature("steps");
        final var stepsInstance = (EList<Object>) basicIdentifier.eGet(stepsFeature, true);
        stepsInstance.add(expression);
    }

}
