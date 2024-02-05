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
package io.sapl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import lombok.SneakyThrows;

class DefaultSAPLInterpreterTransformationTests {

    private static final ObjectMapper               MAPPER           = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final DefaultSAPLInterpreter     INTERPRETER      = new DefaultSAPLInterpreter();
    private static final AnnotationAttributeContext ATTRIBUTE_CTX    = new AnnotationAttributeContext();
    private static final AnnotationFunctionContext  FUNCTION_CTX     = new AnnotationFunctionContext();
    private static final Map<String, JsonNode>      SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<>());

    @BeforeEach
    public void setUp() throws InitializationException {
        FUNCTION_CTX.loadLibrary(SimpleFunctionLibrary.class);
        FUNCTION_CTX.loadLibrary(FilterFunctionLibrary.class);
        var systemUTC                   = Clock.systemUTC();
        var simpleFilterFunctionLibrary = new SimpleFilterFunctionLibrary(systemUTC);
        FUNCTION_CTX.loadLibrary(simpleFilterFunctionLibrary);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("transformationTestCases")
    void validSaplDocumentsParseWithoutError(String testCase, String authorizationSubscription, String saplDocument,
            String expectedResource) {
        assertThat(testCase).isNotNull();
        var expectedDecision = new AuthorizationDecision(Decision.PERMIT,
                Optional.of(MAPPER.readValue(expectedResource, JsonNode.class)), Optional.empty(),
                Optional.empty());
        var subscription     = MAPPER.readValue(authorizationSubscription, AuthorizationSubscription.class);
        assertThatPolicyEvaluationReturnsExpectedDecisionFirstForSubscription(subscription, saplDocument,
                expectedDecision);
    }

    @SneakyThrows
    private static Stream<Arguments> transformationTestCases() {
        // @formatter:off
        return Stream.of(Arguments.of(
                "simpleTransformationWithComment",
                "{}",
                """
                policy "test"
                permit
                transform
                    "teststring"        // This is a dummy comment
                    /* another comment */
                """,
                "\"teststring\""),

                Arguments.of(
                        "simpleFiltering",
                        """
                        { "resource" : "teststring" }
                        """,
                        """
                        policy "test"
                        permit
                        transform
                            resource |- filter.blacken
                        """,
                        "\"XXXXXXXXXX\""),

                Arguments.of(
                        "simpleArrayCondition",
                        """
                        { "resource" : [1, 2, 3, 4, 5] }
                        """,
                        """
                        policy "test"
                        permit
                        transform
                            resource[?(@>2 || @<2)]
                        """,
                        "[1,3,4,5]"),

                Arguments.of(
                        "conditionTransformation",
                        """
                        {
                            "resource":{
                                "array":[
                                    {
                                        "key1":1,
                                        "key2":2
                                    },
                                    {
                                        "key1":3,
                                        "key2":4
                                    },
                                    {
                                        "key1":5,
                                        "key2":6
                                    }
                                ]
                            }
                        }
                        """,
                        """
                        policy "test"
                        permit
                        transform
                            {
                                "array": resource.array[?(@.key1 > 2)]
                            }
                        """,
                        """
                        {
                            "array":[
                                {
                                    "key1":3,
                                    "key2":4
                                },
                                {
                                    "key1":5,
                                    "key2":6
                                }
                            ]
                        }
                        """),

                Arguments.of(
                        "conditionSubTemplateFiltering",
                        """
                        {
                            "resource":{
                                "array":[
                                    {
                                        "key1":1,
                                        "key2":2
                                    },
                                    {
                                        "key1":3,
                                        "key2":4
                                    },
                                    {
                                        "key1":5,
                                        "key2":6
                                    }
                                ]
                            }
                        }
                        """,
                        """
                        policy "test"
                        permit
                        transform
                          {
                            "array": resource.array[?(@.key1 > 2)] :: {
                                 "key20": @.key2
                            }
                          }
                        """,
                        """
                        {
                            "array":[
                                {
                                    "key20":4
                                },
                                {
                                    "key20":6
                                }
                            ]
                        }
                        """),

                Arguments.of(
                        "conditionFilteringRules",
                        """
                        {
                            "resource":{
                                "array":[
                                    {
                                        "key1":1,
                                        "key2":"2"
                                    },
                                    {
                                        "key1":3,
                                        "key2":"4"
                                    },
                                    {
                                        "key1":5,
                                        "key2":"6"
                                    }
                                ]
                            }
                        }
                        """,
                        """
                        policy "test"
                        permit
                        transform
                            {
                                "array": resource.array[?(@.key1 > 2)] |- {
                                    @.key2 : filter.blacken
                                }
                            }
                        """,
                        """
                        {
                            "array":[
                                {
                                    "key1":3,
                                    "key2":"X"
                                },
                                {
                                    "key1":5,
                                    "key2":"X"
                                }
                            ]
                        }
                        """),

            Arguments.of(
                    "arrayLast",
                    """
                    {
                        "resource":{
                            "array":["1","2","3","4","5"]
                        }
                    }
                    """,
                    """
                    policy "test"
                    permit
                    transform
                        {
                            "last": resource.array[-1]
                        }
                    """,
                    """
                    { "last":"5" }
                    """),

        Arguments.of(
                "arraySlicing1",
                """
                {
                    "resource":{
                        "array":["1","2","3","4","5"]
                    }
                }
                """,
                """
                policy "test"
                permit
                transform
                    {
                         "array": resource.array[2:]
                    }
                """,
                """
                { "array":["3","4","5"] }
                """),

        Arguments.of(
            "arraySlicing2",
            """
            {
                "resource":{
                    "array":["1","2","3","4","5"]
                }
            }
            """,
            """
            policy "test"
            permit
            transform
                {
                     "array": resource.array[1:-1:2]
                }
            """,
            """
            { "array":["2","4"] }
            """),

        Arguments.of(
            "arrayExpressionMultipleIndices",
            """
            {
                "resource":{
                    "array":["1","2","3","4","5"],
                    "a_number":1
                }
            }
            """,
            """
            policy "test"
            permit
            transform
                {
                     "array": resource.array[2,4]
                }
            """,
            """
            { "array":["3","5"] }
            """),

        Arguments.of(
            "arrayExplicitEach",
            """
            {
                "resource":{
                    "array":["1","2","3","4","5"]
                }
            }
            """,
            """
            policy "test"
            permit
            transform
                resource |- {
                    each @.array : filter.blacken
                }
            """,
            """
            { "array":["X","X","X","X","X"] }
            """),

        Arguments.of(
            "arrayMultidimensional",
            """
            {
                "resource":{
                    "array":[
                        {"value":"1"},
                        {"value":"2"},
                        {"value":"3"}
                    ]
                }
            }
            """,
            """
            policy "test"
            permit
            transform
                resource |- {
                    @.array[1:].value : filter.blacken
                }
            """,
            """
            {
                "array":[
                    {"value":"1"},
                    {"value":"X"},
                    {"value":"X"}
                ]
            }
            """),

        Arguments.of(
                "recursiveDescent",
                """
                {
                    "resource":{
                        "array":[
                            {"value":"1"},
                            {"value":"2"},
                            {"value":"3"}
                        ]
                    }
                }
                """,
                """
                policy "test"
                permit
                transform
                        resource..value
                """,
                """
                ["1","2","3"]
                """),

        Arguments.of(
                "recursiveDescentInFilterRemove",
                """
                {
                    "resource":{
                        "array":[
                            {"value":"1"},
                            {"value":"2"},
                            {"value":"3"}
                        ],
                        "value":"4"
                    }
                }
                """,
                """
                policy "test"
                permit
                transform
                    resource |- {
                        @..value : filter.remove
                    }
                """,
                """
                {
                    "array":[{},{},{}]
                }
                """),

        Arguments.of(
                "filterReplace",
                """
                {
                    "resource":{
                        "array":[
                            {"name":"John Doe"},
                            {"name":"Jane Doe"}
                        ],
                        "value":"4",
                        "name":"Tom Doe"
                    }
                }
                """,
                """
                policy "test"
                permit
                transform
                    resource |- {
                        @..name : filter.replace("***")
                    }
                """,
                """
                {
                    "array":[
                        {"name":"***"},
                        {"name":"***"}
                    ],
                    "value":"4",
                    "name":"***"
                }
                """));
        // @formatter:on
    }

    private void assertThatPolicyEvaluationReturnsExpectedDecisionFirstForSubscription(
            AuthorizationSubscription authorizationSubscription, String policy,
            AuthorizationDecision expectedDecision) {
        assertThat(
                INTERPRETER.evaluate(authorizationSubscription, policy, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES)
                        .blockFirst(),
                equalTo(expectedDecision));
    }

}
