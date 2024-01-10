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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;

class DefaultSAPLInterpreterTransformationTests {

    private static final ObjectMapper               MAPPER           = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final JsonNodeFactory            JSON             = JsonNodeFactory.instance;
    private static final DefaultSAPLInterpreter     INTERPRETER      = new DefaultSAPLInterpreter();
    private static final AnnotationAttributeContext ATTRIBUTE_CTX    = new AnnotationAttributeContext();
    private static final AnnotationFunctionContext  FUNCTION_CTX     = new AnnotationFunctionContext();
    private static final Map<String, JsonNode>      SYSTEM_VARIABLES = Collections
            .unmodifiableMap(new HashMap<String, JsonNode>());

    @BeforeEach
    public void setUp() throws InitializationException {
        FUNCTION_CTX.loadLibrary(SimpleFunctionLibrary.class);
        FUNCTION_CTX.loadLibrary(FilterFunctionLibrary.class);
        var systemUTC                   = Clock.systemUTC();
        var simpleFilterFunctionLibrary = new SimpleFilterFunctionLibrary(systemUTC);
        FUNCTION_CTX.loadLibrary(simpleFilterFunctionLibrary);
    }

    @Test
    public void simpleTransformationWithComment() throws JsonProcessingException {
        var authorizationSubscription = new AuthorizationSubscription(null, null, null, null);
        var policy                    = """
                policy "test"
                permit
                transform
                    "teststring"        // This is a dummy comment
                    /* another comment */
                """;
        var expectedDecision          = new AuthorizationDecision(Decision.PERMIT,
                Optional.of(MAPPER.<JsonNode>readValue("\"teststring\"\n", JsonNode.class)), Optional.empty(),
                Optional.empty());

        assertThatPolicyEvaluationReturnsExpectedDecisionFirstForSubscription(authorizationSubscription, policy,
                expectedDecision);
    }

    @Test
    void simpleFiltering() throws JsonProcessingException {
        var authorizationSubscription = AuthorizationSubscription.of(null, null, "teststring");
        var policy                    = """
                 policy "test"
                 permit
                 transform
                     resource |- filter.blacken
                """;
        var expectedDecision          = AuthorizationDecision.PERMIT.withResource(JSON.textNode("XXXXXXXXXX"));

        assertThatPolicyEvaluationReturnsExpectedDecisionFirstForSubscription(authorizationSubscription, policy,
                expectedDecision);
    }

    @Test
    void simpleArrayCondition() throws JsonProcessingException {
        var authorizationSubscription = AuthorizationSubscription.of(null, null, new int[] { 1, 2, 3, 4, 5 });
        var policy                    = """
                   policy "test"
                   permit
                   transform
                       resource[?(@>2 || @<2)]
                """;
        var expectedResource          = MAPPER.<JsonNode>readValue("[1,3,4,5]", JsonNode.class);
        var expectedDecision          = AuthorizationDecision.PERMIT.withResource(expectedResource);

        assertThatPolicyEvaluationReturnsExpectedDecisionFirstForSubscription(authorizationSubscription, policy,
                expectedDecision);
    }

    @Test
    void conditionTransformation() throws JsonProcessingException {
        var authorizationSubscription = MAPPER.readValue("""
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
                """, AuthorizationSubscription.class);
        var policy                    = """
                   policy "test"
                   permit
                   transform
                       {
                           "array": resource.array[?(@.key1 > 2)]
                       }
                """;
        var expectedResource          = MAPPER.readValue("""
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
                """, JsonNode.class);
        var expectedDecision          = AuthorizationDecision.PERMIT.withResource(expectedResource);

        assertThatPolicyEvaluationReturnsExpectedDecisionFirstForSubscription(authorizationSubscription, policy,
                expectedDecision);
    }

    @Test
    void conditionSubTemplateFiltering() throws JsonProcessingException {
        var authorizationSubscription = MAPPER.readValue("""
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
                """, AuthorizationSubscription.class);
        var policy                    = """
                     policy "test"
                     permit
                     transform
                         {
                             "array": resource.array[?(@.key1 > 2)] :: {
                                 "key20": @.key2
                             }
                         }
                """;
        var expectedResource          = MAPPER.readValue("""
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
                """, JsonNode.class);
        var expectedDecision          = AuthorizationDecision.PERMIT.withResource(expectedResource);

        assertThatPolicyEvaluationReturnsExpectedDecisionFirstForSubscription(authorizationSubscription, policy,
                expectedDecision);
    }

    @Test
    void conditionFilteringRules() throws JsonProcessingException {
        var authorizationSubscription = MAPPER.readValue("""
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
                """, AuthorizationSubscription.class);
        var policy                    = """
                     policy "test"
                     permit
                     transform
                         {
                             "array": resource.array[?(@.key1 > 2)] |- {
                                 @.key2 : filter.blacken
                             }
                         }
                """;
        var expectedResource          = MAPPER.readValue("""
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
                """, JsonNode.class);
        var expectedDecision          = AuthorizationDecision.PERMIT.withResource(expectedResource);

        assertThatPolicyEvaluationReturnsExpectedDecisionFirstForSubscription(authorizationSubscription, policy,
                expectedDecision);
    }

    @Test
    void arrayLast() throws JsonProcessingException {
        var authorizationSubscription = MAPPER.readValue("""
                 {
                     "resource":{
                         "array":["1","2","3","4","5"]
                     }
                 }
                """, AuthorizationSubscription.class);
        var policy                    = """
                        policy "test"
                        permit
                        transform
                            {
                                "last": resource.array[-1]
                            }
                """;
        var expectedResource          = MAPPER.readValue("""
                   {
                       "last":"5"
                   }
                """, JsonNode.class);
        var expectedDecision          = AuthorizationDecision.PERMIT.withResource(expectedResource);

        assertThatPolicyEvaluationReturnsExpectedDecisionFirstForSubscription(authorizationSubscription, policy,
                expectedDecision);
    }

    @Test
    void arraySlicing1() throws JsonProcessingException {
        var authorizationSubscription = MAPPER.readValue("""
                 {
                     "resource":{
                         "array":["1","2","3","4","5"]
                     }
                 }
                """, AuthorizationSubscription.class);
        var policy                    = """
                        policy "test"
                        permit
                        transform
                            {
                                 "array": resource.array[2:]
                            }
                """;
        var expectedResource          = MAPPER.readValue("""
                       {
                           "array":["3","4","5"]
                       }
                """, JsonNode.class);
        var expectedDecision          = AuthorizationDecision.PERMIT.withResource(expectedResource);

        assertThatPolicyEvaluationReturnsExpectedDecisionFirstForSubscription(authorizationSubscription, policy,
                expectedDecision);
    }

    @Test
    void arraySlicing2() throws JsonProcessingException {
        var authorizationSubscription = MAPPER.readValue("""
                 {
                     "resource":{
                         "array":["1","2","3","4","5"]
                     }
                 }
                """, AuthorizationSubscription.class);
        var policy                    = """
                        policy "test"
                        permit
                        transform
                            {
                                 "array": resource.array[1:-1:2]
                            }
                """;
        var expectedResource          = MAPPER.readValue("""
                       {
                           "array":["2","4"]
                       }
                """, JsonNode.class);
        var expectedDecision          = AuthorizationDecision.PERMIT.withResource(expectedResource);

        assertThatPolicyEvaluationReturnsExpectedDecisionFirstForSubscription(authorizationSubscription, policy,
                expectedDecision);
    }

    @Test
    void arrayExpressionMultipleIndices() throws JsonProcessingException {
        var authorizationSubscription = MAPPER.readValue("""
                 {
                     "resource":{
                         "array":["1","2","3","4","5"],
                         "a_number":1
                     }
                 }
                """, AuthorizationSubscription.class);
        var policy                    = """
                        policy "test"
                        permit
                        transform
                            {
                                 "array": resource.array[2,4]
                            }
                """;
        var expectedResource          = MAPPER.readValue("""
                       {
                           "array":["3","5"]
                       }
                """, JsonNode.class);
        var expectedDecision          = AuthorizationDecision.PERMIT.withResource(expectedResource);

        assertThatPolicyEvaluationReturnsExpectedDecisionFirstForSubscription(authorizationSubscription, policy,
                expectedDecision);
    }

    @Test
    void arrayExplicitEach() throws JsonProcessingException {
        var authorizationSubscription = MAPPER.readValue("""
                 {
                     "resource":{
                         "array":["1","2","3","4","5"]
                     }
                 }
                """, AuthorizationSubscription.class);
        var policy                    = """
                        policy "test"
                        permit
                        transform
                            resource |- {
                                each @.array : filter.blacken
                            }
                """;
        var expectedResource          = MAPPER.readValue("""
                       {
                           "array":["X","X","X","X","X"]
                       }
                """, JsonNode.class);
        var expectedDecision          = AuthorizationDecision.PERMIT.withResource(expectedResource);

        assertThatPolicyEvaluationReturnsExpectedDecisionFirstForSubscription(authorizationSubscription, policy,
                expectedDecision);
    }

    @Test
    void arrayMultidimensional() throws JsonProcessingException {
        var authorizationSubscription = MAPPER.readValue("""
                     {
                         "resource":{
                             "array":[
                                 {"value":"1"},
                                 {"value":"2"},
                                 {"value":"3"}
                             ]
                         }
                     }
                """, AuthorizationSubscription.class);
        var policy                    = """
                        policy "test"
                        permit
                        transform
                            resource |- {
                                @.array[1:].value : filter.blacken
                            }
                """;
        var expectedResource          = MAPPER.readValue("""
                        {
                            "array":[
                                {"value":"1"},
                                {"value":"X"},
                                {"value":"X"}
                            ]
                        }
                """, JsonNode.class);
        var expectedDecision          = AuthorizationDecision.PERMIT.withResource(expectedResource);

        assertThatPolicyEvaluationReturnsExpectedDecisionFirstForSubscription(authorizationSubscription, policy,
                expectedDecision);
    }

    @Test
    void recursiveDescent() throws JsonProcessingException {
        var authorizationSubscription = MAPPER.readValue("""
                     {
                         "resource":{
                             "array":[
                                 {"value":"1"},
                                 {"value":"2"},
                                 {"value":"3"}
                             ]
                         }
                     }
                """, AuthorizationSubscription.class);
        var policy                    = """
                        policy "test"
                        permit
                        transform
                                resource..value
                """;
        var expectedResource          = MAPPER.readValue("[\"1\",\"2\",\"3\"]", JsonNode.class);
        var expectedDecision          = AuthorizationDecision.PERMIT.withResource(expectedResource);

        assertThatPolicyEvaluationReturnsExpectedDecisionFirstForSubscription(authorizationSubscription, policy,
                expectedDecision);
    }

    @Test
    void recursiveDescentInFilterRemove() throws JsonProcessingException {
        var authorizationSubscription = MAPPER.readValue("""
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
                """, AuthorizationSubscription.class);
        var policy                    = """
                    policy "test"
                    permit
                    transform
                        resource |- {
                            @..value : filter.remove
                        }
                """;
        var expectedResource          = MAPPER.readValue("""
                         {
                             "array":[{},{},{}]
                         }
                """, JsonNode.class);
        var expectedDecision          = AuthorizationDecision.PERMIT.withResource(expectedResource);

        assertThatPolicyEvaluationReturnsExpectedDecisionFirstForSubscription(authorizationSubscription, policy,
                expectedDecision);
    }

    @Test
    void filterReplace() throws JsonProcessingException {
        var authorizationSubscription = MAPPER.readValue("""
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
                """, AuthorizationSubscription.class);
        var policy                    = """
                         policy "test"
                         permit
                         transform
                             resource |- {
                                 @..name : filter.replace("***")
                             }
                """;
        var expectedResource          = MAPPER.readValue("""
                           {
                               "array":[
                                   {"name":"***"},
                                   {"name":"***"}
                               ],
                               "value":"4",
                               "name":"***"
                           }
                """, JsonNode.class);
        var expectedDecision          = AuthorizationDecision.PERMIT.withResource(expectedResource);

        assertThatPolicyEvaluationReturnsExpectedDecisionFirstForSubscription(authorizationSubscription, policy,
                expectedDecision);
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
