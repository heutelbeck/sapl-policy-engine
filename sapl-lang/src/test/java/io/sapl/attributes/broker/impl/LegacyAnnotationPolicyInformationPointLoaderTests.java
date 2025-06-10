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
package io.sapl.attributes.broker.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.Bool;
import io.sapl.api.validation.Schema;
import io.sapl.api.validation.Text;
import io.sapl.attributes.broker.api.AttributeBrokerException;
import io.sapl.attributes.broker.api.AttributeStreamBroker;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.testutil.ParserUtil;
import io.sapl.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

class LegacyAnnotationPolicyInformationPointLoaderTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void when_classHasNoAnnotation_fail() {
        final var pipLoader = newPipLoader();
        pipLoader.loadPolicyInformationPoint(new TestPIP());
        assertThrows(AttributeBrokerException.class, () -> pipLoader.loadPolicyInformationPoint(
                "I am an instance of a class without a @PolicyInformationPoint annotation"));
    }

    @Test
    void when_classHasNoAttributesDeclared_fail() {
        @PolicyInformationPoint
        class PIP {

        }
        final var pip = new PIP();
        assertLoadingPipThrowsAttributeBrokerException(pip);
        assertLoadingPipThrowsAttributeBrokerException(PIP.class);
    }

    @Test
    void when_pipWithSameNameExists_fail() {
        @PolicyInformationPoint(name = "somePip")
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> x() {
                return null;
            }

        }

        final var pip       = new PIP();
        final var pipLoader = newPipLoader();
        pipLoader.loadPolicyInformationPoint(pip);
        assertThatThrownBy(() -> pipLoader.loadPolicyInformationPoint(pip)).isInstanceOf(AttributeBrokerException.class)
                .hasMessageContaining("collides");
    }

    @Test
    void when_firstParameterOfAttributeIllegal_fail() {
        @PolicyInformationPoint
        class PIP {

            @Attribute
            public static Flux<Val> x(String x) {
                return null;
            }

        }
        assertLoadingPipThrowsAttributeBrokerException(PIP.class);
    }

    @Test
    void when_returnTypeIllegal_fail() {
        @PolicyInformationPoint
        class PIP {

            @Attribute
            void x() {
                // NOOP
            }

        }
        final var pip = new PIP();
        assertLoadingPipThrowsAttributeBrokerException(pip);
    }

    @Test
    void when_returnTypeIllegalFluxType_fail() {
        @PolicyInformationPoint
        class PIP {

            @Attribute
            public Flux<String> x() {
                return null;
            }

        }
        final var pip = new PIP();
        assertLoadingPipThrowsAttributeBrokerException(pip);
    }

    @Test
    void when_returnTypeIllegalGenericVal_fail() {
        @PolicyInformationPoint
        class PIP {

            @Attribute
            public List<Val> x() {
                return null;
            }

        }
        final var pip = new PIP();
        assertLoadingPipThrowsAttributeBrokerException(pip);
    }

    @Test
    void when_firstAndOnlyParameterVal_loadSuccessful() {
        @PolicyInformationPoint
        class PIP {

            @Attribute
            public Flux<Val> x(Val entity) {
                return null;
            }

        }

        final var pip                   = new PIP();
        final var attributeStreamBroker = new CachingAttributeStreamBroker();
        final var pipLoader             = new AnnotationPolicyInformationPointLoader(attributeStreamBroker,
                new ValidatorFactory(new ObjectMapper()));
        assertDoesNotThrow(() -> pipLoader.loadPolicyInformationPoint(pip));
        assertThat(attributeStreamBroker.getAvailableLibraries().contains("PIP"), is(true));
        assertThat(attributeStreamBroker.providedFunctionsOfLibrary("PIP").contains("x"), is(true));
        assertThat(attributeStreamBroker.isProvidedFunction("PIP.x"), is(Boolean.TRUE));
        assertThat(new ArrayList<>(attributeStreamBroker.getDocumentation()).get(0).getName(), is("PIP"));
    }

    @Test
    void when_noPip_providedIsEmpty() {
        final var attributeStreamBroker = new CachingAttributeStreamBroker();
        assertThat(attributeStreamBroker.providedFunctionsOfLibrary("PIP").isEmpty(), is(true));
        assertThat(attributeStreamBroker.getDocumentation().isEmpty(), is(true));
    }

    @Test
    void when_firstAndOnlyParameterVariablesMap_loadSuccessful() {
        @PolicyInformationPoint
        class PIP {

            @EnvironmentAttribute
            public static Flux<Val> x(Map<String, Val> variables) {
                return null;
            }

        }
        final var pip = new PIP();
        assertLoadingPipDoesNotThrow(pip);
        assertLoadingStaticPipDoesNotThrow(PIP.class);
    }

    @Test
    void when_nonStaticButLoadedViaClass_thenFail() {
        @PolicyInformationPoint
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> x(Map<String, JsonNode> variables) {
                return null;
            }

        }
        assertLoadingPipThrowsAttributeBrokerException(PIP.class);
    }

    @Test
    void when_firstAndOnlyParameterMapWithBadKeyType_fail() {
        @PolicyInformationPoint
        class PIP {

            @Attribute
            public Flux<Val> x(Map<Long, JsonNode> variables) {
                return null;
            }

        }
        final var pip = new PIP();
        assertLoadingPipThrowsAttributeBrokerException(pip);
    }

    @Test
    void when_firstAndOnlyParameterMapWithBadValueType_fail() {
        @PolicyInformationPoint
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> x(Map<String, String> variables) {
                return null;
            }

        }
        final var pip = new PIP();
        assertLoadingPipThrowsAttributeBrokerException(pip);

        @PolicyInformationPoint
        class PIP2 {

            @EnvironmentAttribute
            public Flux<Val> x(Map<Double, String> variables) {
                return null;
            }

        }

        final var pip2 = new PIP2();
        assertLoadingPipThrowsAttributeBrokerException(pip2);

        @PolicyInformationPoint
        class PIP3 {

            @EnvironmentAttribute
            public Flux<Val> x(Map<Double, JsonNode> variables) {
                return null;
            }

        }

        final var pip3 = new PIP3();
        assertLoadingPipThrowsAttributeBrokerException(pip3);

    }

    @Test
    void when_firstAndOnlyParameterOfEnvAttributeVal_loadSuccessful() {
        @PolicyInformationPoint
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> x(Val firstParameter) {
                return null;
            }

        }

        final var pip = new PIP();
        assertLoadingPipDoesNotThrow(pip);
    }

    @Test
    void when_firstAndOnlyParameterNotAVal_fail() {
        @PolicyInformationPoint
        class PIP {

            @Attribute
            public Flux<Val> x(Object firstParameter) {
                return null;
            }

        }

        final var pip = new PIP();
        assertLoadingPipThrowsAttributeBrokerException(pip, String
                .format(AnnotationPolicyInformationPointLoader.NON_VAL_PARAMETER_AT_METHOD_S_ERROR, "x", "Object"));
    }

    @Test
    void when_noParameter_fail() {
        @PolicyInformationPoint
        class PIP {

            @Attribute
            public Flux<Val> x() {
                return null;
            }

        }

        final var pip = new PIP();

        assertLoadingPipThrowsAttributeBrokerException(pip,
                String.format(AnnotationPolicyInformationPointLoader.FIRST_PARAMETER_NOT_PRESENT_S_ERROR, "x"));
    }

    @Test
    void when_someParamBAdType_fail() {
        @PolicyInformationPoint
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> x(Val firstParameter, String x) {
                return null;
            }

        }

        final var pip = new PIP();
        assertLoadingPipThrowsAttributeBrokerException(pip);
    }

    @Test
    void when_firstAndOnlyParameterIsVarArgsOfVal_loadSuccessful() {
        @PolicyInformationPoint
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> x(Val... varArgsParams) {
                return null;
            }

        }

        final var pip = new PIP();
        assertLoadingPipDoesNotThrow(pip);
    }

    @Test
    void when_firstAndOnlyParameterIsArrayOfVal_loadSuccessful() {
        @PolicyInformationPoint
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> x(Val[] varArgsParams) {
                return null;
            }

        }

        final var pip = new PIP();
        assertLoadingPipDoesNotThrow(pip);
    }

    @Test
    void when_arrayFollowedBYSomething_failImport() {
        @PolicyInformationPoint
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> x(Val[] varArgsParams, String iAmTooMuchToHandle) {
                return null;
            }

        }

        final var pip = new PIP();
        assertLoadingPipThrowsAttributeBrokerException(pip);
    }

    @Test
    void when_firstAndOnlyParameterIsVarArgsString_fail() {
        @PolicyInformationPoint
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> x(String... varArgsParams) {
                return null;
            }

        }

        final var pip = new PIP();
        assertLoadingPipThrowsAttributeBrokerException(pip);
    }

    @Test
    void when_differentNames_noCollision() {
        @PolicyInformationPoint
        class PIP {

            @Attribute
            public Flux<Val> x(Val entity) {
                return null;
            }

            @Attribute
            public Flux<Val> y(Val entity) {
                return null;
            }

        }

        final var pip = new PIP();
        assertLoadingPipDoesNotThrow(pip);
    }

    @Test
    void when_envAndNonEnv_noCollision() {
        @PolicyInformationPoint
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> x() {
                return null;
            }

            @Attribute
            public Flux<Val> x(Val entity) {
                return null;
            }

        }

        final var pip = new PIP();
        assertLoadingPipDoesNotThrow(pip);
    }

    @Test
    void when_varargAndNonVarArg_noCollision() {
        @PolicyInformationPoint
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> x(Val a) {
                return null;
            }

            @EnvironmentAttribute
            public Flux<Val> x(Val... a) {
                return null;
            }

        }

        final var pip = new PIP();
        assertLoadingPipDoesNotThrow(pip);
    }

    @Test
    void when_twoVarArg_collision() {
        @PolicyInformationPoint
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> x(Val[] a) {
                return null;
            }

            @EnvironmentAttribute(name = "x")
            public Flux<Val> y(Val... a) {
                return null;
            }

        }

        final var pip = new PIP();
        assertLoadingPipThrowsAttributeBrokerException(pip);
    }

    @Test
    void when_sameNumberOFParams_collision() {
        @PolicyInformationPoint
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> x(Val a) {
                return null;
            }

            @EnvironmentAttribute(name = "x")
            public Flux<Val> y(Val b) {
                return null;
            }

        }

        final var pip = new PIP();
        assertLoadingPipThrowsAttributeBrokerException(pip);
    }

    @Test
    void when_evaluateUnknownAttribute_fails() throws IOException {
        final var broker     = new CachingAttributeStreamBroker();
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("<test.envAttribute(\"param1\",\"param2\")>");
        StepVerifier.create(expression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNextMatches(valErrorContains("No unique policy information point found")).thenCancel().verify();
    }

    @Test
    void when_varArgsWithVariablesEnvironmentAttribute_evaluates() throws AttributeBrokerException, IOException {
        @PolicyInformationPoint(name = "test")
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> envAttribute(Map<String, Val> variables, @Text Val... varArgsParams) {
                return Flux.just(varArgsParams[1]);
            }

        }

        final var pip        = new PIP();
        final var broker     = brokerWithPip(pip);
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("<test.envAttribute(\"param1\",\"param2\")>");
        StepVerifier.create(expression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNext(Val.of("param2")).thenCancel().verify();
    }

    @Test
    void when_varArgsWithVariablesAttribute_evaluates() throws AttributeBrokerException, IOException {
        @PolicyInformationPoint(name = "test")
        class PIP {

            @Attribute
            public Flux<Val> attribute(Val entity, Map<String, Val> variables, @Text Val... varArgsParams) {
                return Flux.just(varArgsParams[1]);
            }

        }

        final var pip        = new PIP();
        final var broker     = brokerWithPip(pip);
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("\"\".<test.attribute(\"param1\",\"param2\")>");
        StepVerifier.create(expression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNext(Val.of("param2")).thenCancel().verify();
    }

    @Test
    void when_varArgsWithVariablesAndTwoAttributes_evaluates() throws AttributeBrokerException, IOException {
        @PolicyInformationPoint(name = "test")
        class PIP {

            @Attribute
            public Flux<Val> attribute(Val entity, Map<String, Val> variables, @Text Val param1, @Text Val param2) {
                return Flux.just(param2);
            }

        }

        final var pip        = new PIP();
        final var broker     = brokerWithPip(pip);
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("\"\".<test.attribute(\"param1\",\"param2\")>");
        StepVerifier.create(expression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNext(Val.of("param2")).thenCancel().verify();
    }

    @Test
    void when_varArgsWithVariablesAndVarArgsAndNoArguments_evaluates() throws AttributeBrokerException, IOException {
        @PolicyInformationPoint(name = "test")
        class PIP {

            @Attribute
            public Flux<Val> attribute(Val entity, Map<String, Val> variables, @Text Val... params) {
                return Flux.just(entity);
            }

        }

        final var pip        = new PIP();
        final var broker     = brokerWithPip(pip);
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("\"\".<test.attribute>");
        StepVerifier.create(expression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNext(Val.of("")).thenCancel().verify();
    }

    @Test
    void when_varArgsWithoutVariablesAndVarArgsAndNoArguments_evaluates() throws AttributeBrokerException, IOException {
        @PolicyInformationPoint(name = "test")
        class PIP {

            @Attribute
            public Flux<Val> attribute(Val entity, @Text Val... params) {
                return Flux.just(entity);
            }
        }

        final var pip        = new PIP();
        final var broker     = brokerWithPip(pip);
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("\"\".<test.attribute(\"A\",\"B\")>");
        StepVerifier.create(expression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNext(Val.of("")).thenCancel().verify();
    }

    @Test
    void when_varArgsNoVariablesEnvironmentAttribute_evaluates() throws AttributeBrokerException, IOException {
        @PolicyInformationPoint(name = "test")
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> envAttribute(@Text Val... varArgsParams) {
                return Flux.just(varArgsParams[1]);
            }

        }

        final var pip        = new PIP();
        final var broker     = brokerWithPip(pip);
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("<test.envAttribute(\"param1\",\"param2\")>");
        StepVerifier.create(expression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNext(Val.of("param2")).thenCancel().verify();
    }

    @Test
    void when_varsAndParamEnvironmentAttribute_evaluates() throws AttributeBrokerException, IOException {
        @PolicyInformationPoint(name = "test")
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> envAttribute(Map<String, Val> variables, @Text Val param1) {
                return Flux.just(param1);
            }

        }

        final var pip        = new PIP();
        final var broker     = brokerWithPip(pip);
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("<test.envAttribute(\"param1\")>");
        StepVerifier.create(expression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNext(Val.of("param1")).thenCancel().verify();
    }

    @Test
    void when_varArgsAndTwoParamEnvironmentAttribute_evaluatesExactParameterMatch()
            throws AttributeBrokerException, IOException {
        @PolicyInformationPoint(name = "test")
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> envAttribute(Map<String, Val> variables, @Text Val param1, @Text Val param2) {
                return Flux.just(param1);
            }

            @EnvironmentAttribute
            public Flux<Val> envAttribute(Map<String, Val> variables, @Text Val... varArgsParams) {
                return Flux.just(varArgsParams[1]);
            }

        }

        final var pip        = new PIP();
        final var broker     = brokerWithPip(pip);
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("<test.envAttribute(\"param1\",\"param2\")>");
        StepVerifier.create(expression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNext(Val.of("param1")).thenCancel().verify();
    }

    @Test
    void when_varArgsEnvironmentAttribute_calledWithNoParams_evaluatesVarArgsWithEmptyParamArray()
            throws AttributeBrokerException, IOException {
        @PolicyInformationPoint(name = "test")
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> envAttribute(Map<String, Val> variables, @Text Val... varArgsParams) {
                return Flux.just(Val.of(varArgsParams.length));
            }

        }

        final var pip        = new PIP();
        final var broker     = brokerWithPip(pip);
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("<test.envAttribute>");
        StepVerifier.create(expression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNext(Val.of(0)).thenCancel().verify();
    }

    @Test
    void when_noArgsEnvironmentAttribute_called_evaluates() throws AttributeBrokerException, IOException {
        @PolicyInformationPoint(name = "test")
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> envAttribute() {
                return Flux.just(Val.of("OK"));
            }

        }

        final var pip        = new PIP();
        final var broker     = brokerWithPip(pip);
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("<test.envAttribute>");
        StepVerifier.create(expression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNext(Val.of("OK")).thenCancel().verify();
    }

    @Test
    void when_noArgsEnvironmentAttribute_calledAndFails_evaluatesToError()
            throws AttributeBrokerException, IOException {
        @PolicyInformationPoint(name = "test")
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> envAttribute() {
                throw new IllegalStateException("INTENDED ERROR FROM TEST");
            }

        }

        final var pip        = new PIP();
        final var broker     = brokerWithPip(pip);
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("<test.envAttribute>");
        StepVerifier.create(expression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNextMatches(valErrorText("INTENDED ERROR FROM TEST")).thenCancel().verify();
    }

    @Test
    void when_noArgsAttribute_calledAndFails_evaluatesToError() throws AttributeBrokerException, IOException {
        @PolicyInformationPoint(name = "test")
        class PIP {

            @Attribute
            public Flux<Val> attribute(Val entity) {
                throw new IllegalStateException("INTENDED ERROR FROM TEST");
            }

        }

        final var pip        = new PIP();
        final var broker     = brokerWithPip(pip);
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("\"\".<test.attribute>");
        StepVerifier.create(expression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNextMatches(valErrorText("INTENDED ERROR FROM TEST")).thenCancel().verify();
    }

    private Predicate<Val> valErrorText(String errorMessage) {
        return val -> val.isError() && errorMessage.equals(val.getMessage());
    }

    private Predicate<Val> valErrorTextContains(String errorMessage) {
        return val -> val.isError() && val.getMessage().contains(errorMessage);
    }

    private Predicate<Val> valErrorContains(String errorMessage) {
        return val -> val.isError() && val.getMessage().contains(errorMessage);
    }

    @Test
    void when_noArgsAttribute_called_evaluates() throws AttributeBrokerException, IOException {
        @PolicyInformationPoint(name = "test")
        class PIP {

            @Attribute
            public Flux<Val> attribute(Val entity) {
                return Flux.just(entity);
            }

        }

        final var pip        = new PIP();
        final var broker     = brokerWithPip(pip);
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("\"\".<test.attribute>");
        StepVerifier.create(expression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNext(Val.of("")).thenCancel().verify();
    }

    @Test
    void when_unknownAttribute_called_evaluatesToError() throws IOException {
        final var broker     = new CachingAttributeStreamBroker();
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("\"\".<test.envAttribute>");
        StepVerifier.create(expression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNextMatches(
                        valErrorTextContains("No unique policy information point found for AttributeFinderInvocation"))
                .thenCancel().verify();
    }

    @Test
    void when_twoParamEnvironmentAttribute_calledWithOneParam_evaluatesToError()
            throws AttributeBrokerException, IOException {
        @PolicyInformationPoint(name = "test")
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> envAttribute(Map<String, Val> variables, @Text Val param1, @Text Val param2) {
                return Flux.just(param1);
            }

        }

        final var pip        = new PIP();
        final var broker     = brokerWithPip(pip);
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("<test.envAttribute(\"param1\")>");
        StepVerifier.create(expression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNextMatches(valErrorContains("No unique policy information point found")).thenCancel().verify();
    }

    @Test
    void when_varArgsWithVariablesEnvironmentAttributeAndBadParamType_evaluatesToError()
            throws AttributeBrokerException, IOException {
        @PolicyInformationPoint(name = "test")
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> envAttribute(Map<String, Val> variables, @Bool Val... varArgsParams) {
                return Flux.just(varArgsParams[1]);
            }

        }

        final var pip        = new PIP();
        final var broker     = brokerWithPip(pip);
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("<test.envAttribute(\"param1\",\"param2\")>");
        StepVerifier.create(expression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNextMatches(valErrorText("Expected a Boolean value, but got \"param1\"")).thenCancel().verify();
    }

    @Test
    void when_argWithParamSchema_validatesCorrectly() throws AttributeBrokerException, IOException {

        @PolicyInformationPoint(name = "test")
        class PIP {

            static final String PERSON_SCHEMA = """
                    {
                      "$schema": "http://json-schema.org/draft-07/schema#",
                      "$id": "https://example.com/schemas/regions",
                      "type": "object",
                      "properties": {
                    	"name": { "type": "string" }
                      }
                    }
                    """;

            @EnvironmentAttribute
            public Flux<Val> envAttribute(Map<String, Val> variable, @Schema(value = PERSON_SCHEMA) Val a1) {
                return Flux.just(a1);
            }

            @Attribute(schema = PERSON_SCHEMA)
            public Flux<Val> attributeWithAnnotation(Val a, Val a1) {
                return Flux.just(a1);
            }

        }

        final var pip       = new PIP();
        final var broker    = brokerWithPip(pip);
        final var variables = Map.of("key1", Val.of("valueOfKey"));

        final var validExpression = ParserUtil.expression("<test.envAttribute({\"name\": \"Joe\"})>");
        final var expected        = new ObjectMapper().readTree("{\"name\": \"Joe\"}\")>");
        StepVerifier.create(validExpression.evaluate().log().contextWrite(this.constructContext(broker, variables)))
                .expectNext(Val.of(expected)).thenCancel().verify();

        final var invalidExpression = ParserUtil.expression("<test.envAttribute({\"name\": 23})>");
        String    errorMessage      = """
                Illegal parameter type. Parameter does not comply with required schema. Got: {"name":23} Expected schema: {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "$id": "https://example.com/schemas/regions",
                  "type": "object",
                  "properties": {
                	"name": { "type": "string" }
                  }
                }
                """;
        StepVerifier.create(invalidExpression.evaluate().contextWrite(this.constructContext(broker, variables)))
                .expectNextMatches(valErrorText(errorMessage)).thenCancel().verify();
    }

    @Test
    void generatesCodeTemplates() throws AttributeBrokerException {

        @PolicyInformationPoint(name = "test")
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> a(Map<String, Val> variables, @Bool @Text Val... varArgsParams) {
                return Flux.just(varArgsParams[1]);
            }

            @EnvironmentAttribute
            public Flux<Val> a(@Bool @Text Val a1, @Bool Val a2) {
                return Flux.just(a1);
            }

            @EnvironmentAttribute
            public Flux<Val> a2(Val a1, Val a2) {
                return Flux.just(a1);
            }

            @EnvironmentAttribute
            public Flux<Val> a2() {
                return Flux.empty();
            }

            @Attribute
            public Flux<Val> x(Val entity, Map<String, Val> variables, @Bool @Text Val... varArgsParams) {
                return Flux.just(varArgsParams[1]);
            }

            @Attribute
            public Flux<Val> x(Val entity, @NotNull @Bool @Text Val a1, @Bool Val a2) {
                return Flux.just(a1);
            }

            @Attribute
            public Flux<Val> x2(Val entity, Val a1, Val a2) {
                return Flux.just(a1);
            }

        }

        final var pip = new PIP();
        final var sut = brokerWithPip(pip);

        final var expectedEnvironmentTemplates = new String[] { "<test.a(a1, a2)>", "<test.a(varArgsParams...)>",
                "<test.a2(a1, a2)>", "<test.a2>" };
        final var actualEnvironmentTemplates   = sut.getEnvironmentAttributeCodeTemplates();
        assertThat(actualEnvironmentTemplates, containsInAnyOrder(expectedEnvironmentTemplates));

        final var expectedNonEnvironmentTemplates = new String[] { "<test.x2(a1, a2)>", "<test.x(varArgsParams...)>",
                "<test.x(a1, a2)>" };
        final var actualNonEnvironmentTemplates   = sut.getAttributeCodeTemplates();
        assertThat(actualNonEnvironmentTemplates, containsInAnyOrder(expectedNonEnvironmentTemplates));

        assertThat(sut.getAvailableLibraries(), containsInAnyOrder("test"));
        assertThat(sut.getAllFullyQualifiedFunctions().size(), is(7));
        assertThat(sut.getAllFullyQualifiedFunctions(),
                containsInAnyOrder("test.x2", "test.a", "test.a", "test.a2", "test.a2", "test.x", "test.x"));
    }

    @Test
    void when_environmentAttributeButOnlyNonEnvAttributePresent_fail() throws AttributeBrokerException, IOException {
        @PolicyInformationPoint(name = "test")
        class PIP {

            @Attribute
            public Flux<Val> attribute(Val entity, Map<String, Val> variables, @Text Val... varArgsParams) {
                return Flux.just(varArgsParams[1]);
            }

        }

        final var pip        = new PIP();
        final var broker     = brokerWithPip(pip);
        final var variables  = Map.of("key1", Val.of("valueOfKey"));
        final var expression = ParserUtil.expression("<test.attribute(\"param1\",\"param2\")>");
        StepVerifier.create(expression.evaluate().log().contextWrite(this.constructContext(broker, variables)))
                .expectNextMatches(
                        valErrorTextContains("No unique policy information point found for AttributeFinderInvocation"))
                .thenCancel().verify();
    }

    private Function<Context, Context> constructContext(AttributeStreamBroker attributeStreamBroker,
            Map<String, Val> variables) {
        return ctx -> {
            ctx = AuthorizationContext.setAttributeStreamBroker(ctx, attributeStreamBroker);
            ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
            ctx = AuthorizationContext.setVariables(ctx, variables);
            return ctx;
        };
    }

    @Test
    void addsDocumentedAttributeCodeTemplates() throws AttributeBrokerException {

        final String pipName        = "test";
        final String pipDescription = "description";

        @PolicyInformationPoint(name = pipName, description = pipDescription)
        class PIP {

            @EnvironmentAttribute
            public Flux<Val> empty() {
                return Flux.empty();
            }
        }

        final var pip    = new PIP();
        final var broker = brokerWithPip(pip);

        final var actualTemplates = broker.getDocumentedAttributeCodeTemplates();
        assertThat(actualTemplates, hasEntry(pipName, pipDescription));
    }

    @Test
    void when_nonJsonSchema_then_fail() {

        @PolicyInformationPoint(name = "test", description = "description")
        class PIP {

            @EnvironmentAttribute(schema = "][")
            public Flux<Val> empty() {
                return Flux.empty();
            }
        }

        final var pip = new PIP();
        assertLoadingPipThrowsAttributeBrokerException(pip);
    }

    @Test
    void when_resourcesSchemaLoadError_then_fail() {

        @PolicyInformationPoint(name = "test", description = "description")
        class PIP {

            @EnvironmentAttribute(pathToSchema = "/i_do_not_exist.json")
            public Flux<Val> empty() {
                return Flux.empty();
            }
        }

        final var pip = new PIP();
        assertLoadingPipThrowsAttributeBrokerException(pip);
    }

    @Test
    void when_multipleSchemaSources_then_fail() {

        @PolicyInformationPoint(name = "test", description = "description")
        class PIP {

            @EnvironmentAttribute(schema = "{}", pathToSchema = "/i_do_not_exist.json")
            public Flux<Val> empty() {
                return Flux.empty();
            }
        }
        final var pip = new PIP();
        assertLoadingPipThrowsAttributeBrokerException(pip);
    }

    private AttributeStreamBroker brokerWithPip(Object pip) {
        final var attributeStreamBroker = new CachingAttributeStreamBroker();
        final var loader                = new AnnotationPolicyInformationPointLoader(attributeStreamBroker,
                new ValidatorFactory(new ObjectMapper()));
        loader.loadPolicyInformationPoint(pip);
        return attributeStreamBroker;
    }

    private void assertLoadingPipDoesNotThrow(Object pip) {
        final var pipLoader = newPipLoader();
        assertDoesNotThrow(() -> pipLoader.loadPolicyInformationPoint(pip));
    }

    private void assertLoadingStaticPipDoesNotThrow(Class<?> pip) {
        final var pipLoader = newPipLoader();
        assertDoesNotThrow(() -> pipLoader.loadStaticPolicyInformationPoint(pip));
    }

    private void assertLoadingPipThrowsAttributeBrokerException(Object pip) {
        final var pipLoader = newPipLoader();
        assertThatThrownBy(() -> pipLoader.loadPolicyInformationPoint(pip))
                .isInstanceOf(AttributeBrokerException.class);
    }

    private void assertLoadingPipThrowsAttributeBrokerException(Object pip, String errorMessage) {
        final var pipLoader = newPipLoader();
        assertThatThrownBy(() -> pipLoader.loadPolicyInformationPoint(pip)).isInstanceOf(AttributeBrokerException.class)
                .hasMessage(errorMessage);
    }

    private void assertLoadingPipThrowsAttributeBrokerException(Class<?> pipClass) {
        final var pipLoader = newPipLoader();
        assertThatThrownBy(() -> pipLoader.loadPolicyInformationPoint(pipClass))
                .isInstanceOf(AttributeBrokerException.class);
    }

    private AnnotationPolicyInformationPointLoader newPipLoader() {
        final var attributeStreamBroker = new CachingAttributeStreamBroker();
        return new AnnotationPolicyInformationPointLoader(attributeStreamBroker, new ValidatorFactory(MAPPER));
    }

}
