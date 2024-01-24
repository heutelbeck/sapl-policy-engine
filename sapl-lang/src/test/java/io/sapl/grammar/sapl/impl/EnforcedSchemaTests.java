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
package io.sapl.grammar.sapl.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.SchemaValidationLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.grammar.sapl.impl.util.MatchingUtil;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.SimpleFunctionLibrary;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import lombok.SneakyThrows;

public class EnforcedSchemaTests {
    private static final ObjectMapper           MAPPER      = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
    private static AnnotationAttributeContext   attributeContext;
    private static AnnotationFunctionContext    functionContext;

    @BeforeAll
    static void beforeAll() throws JsonProcessingException, InitializationException {
        attributeContext = new AnnotationAttributeContext();
        functionContext  = new AnnotationFunctionContext();
        functionContext.loadLibrary(SimpleFunctionLibrary.class);
        functionContext.loadLibrary(SchemaValidationLibrary.class);
        functionContext.loadLibrary(FilterFunctionLibrary.class);
        functionContext.loadLibrary(StandardFunctionLibrary.class);
    }

    @Test
    void when_noEnforcementAndNoTarget_then_matches() throws JsonProcessingException {
        var authzSubscription = """
                {
                    "subject"  : "willi",
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        var document          = """
                policy "test"
                permit
                """;

        assertMatchEvaluation(authzSubscription, document, true);
    }

    @Test
    void when_noEnforcementButWithTarget_then_matches() throws JsonProcessingException {
        var authzSubscription = """
                {
                    "subject"  : "willi",
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        var document          = """
                policy "test"
                permit true
                """;

        assertMatchEvaluation(authzSubscription, document, true);
    }

    @Test
    void when_enforcedSubjectSchemaAndValidSubject_then_matches() throws JsonProcessingException {
        var authzSubscription = """
                {
                    "subject"  : "willi",
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        var document          = """
                subject     enforced schema {
                                                "$schema": "https://json-schema.org/draft/2020-12/schema",
                                                "$id": "https://example.com/product.schema.json",
                                                "title": "Schema for Subject",
                                                "description": "A Subject is just a String",
                                                "type": "string"
                                            }
                policy "test"
                permit
                """;

        assertMatchEvaluation(authzSubscription, document, true);
    }

    @Test
    void when_enforcedSubjectSchemaAndValidSubjectButTargetNoMatch_then_noMatch() throws JsonProcessingException {
        var authzSubscription = """
                {
                    "subject"  : "willi",
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        var document          = """
                subject     enforced schema {
                                                "$schema": "https://json-schema.org/draft/2020-12/schema",
                                                "$id": "https://example.com/product.schema.json",
                                                "title": "Schema for Subject",
                                                "description": "A Subject is just a String",
                                                "type": "string"
                                            }
                policy "test"
                permit false
                """;

        assertMatchEvaluation(authzSubscription, document, false);
    }

    @Test
    void when_schemaError_then_error() throws JsonProcessingException {
        var authzSubscription = """
                {
                    "subject"  : "willi",
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        var document          = """
                subject     enforced schema {
                                                "$schema": "https://json-schema.org/draft/2020-12/schema",
                                                "$id": "https://example.com/product.schema.json",
                                                "title": "Schema for Subject",
                                                "description": "A Subject is just a String",
                                                "type": 1/0
                                            }
                policy "test"
                permit 1/0
                """;

        assertMatchError(authzSubscription, document);
    }

    @Test
    void when_elementError_then_error() throws JsonProcessingException {
        var authzSubscription = """
                {
                    "subject"  : "willi",
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        var document          = """
                subject     enforced schema {
                                                "$schema": "https://json-schema.org/draft/2020-12/schema",
                                                "$id": "https://example.com/product.schema.json",
                                                "title": "Schema for Subject",
                                                "description": "A Subject is just a String",
                                                "type": "string"
                                            }
                policy "test"
                permit 1/0
                """;

        assertMatchError(authzSubscription, document);
    }

    @Test
    void when_enforcedSubjectAndActionSchemaAndValidSubjectAndAction_then_bothMustMatch()
            throws JsonProcessingException {
        var bothMatchSubscription      = """
                {
                    "subject"  : "willi",
                    "action"   : 123,
                    "resource" : "ice cream"
                }
                """;
        var actionNoMatchSubscription  = """
                {
                    "subject"  : "willi",
                    "action"   : true,
                    "resource" : "ice cream"
                }
                """;
        var subjectNoMatchSubscription = """
                {
                    "subject"  : 987,
                    "action"   : 123,
                    "resource" : "ice cream"
                }
                """;
        var document                   = """
                subject     enforced schema {
                                                "$schema": "https://json-schema.org/draft/2020-12/schema",
                                                "$id": "https://example.com/product.schema.json",
                                                "title": "Schema for Subject",
                                                "description": "A Subject is just a String",
                                                "type": "string"
                                            }
                action      enforced schema {
                                                "$schema": "https://json-schema.org/draft/2020-12/schema",
                                                "$id": "https://example.com/product.schema.json",
                                                "title": "Schema for Action",
                                                "description": "An action is just a number",
                                                "type": "number"
                                            }
                policy "test"
                permit
                """;

        assertMatchEvaluation(bothMatchSubscription, document, true);
        assertMatchEvaluation(actionNoMatchSubscription, document, false);
        assertMatchEvaluation(subjectNoMatchSubscription, document, false);
    }

    @Test
    void when_enforcedSubjectSchemaAndInvalidSubject_then_notMatching() throws JsonProcessingException {
        var authzSubscription = """
                {
                    "subject"  : 123,
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        var document          = """
                subject     enforced schema {
                                                "$schema": "https://json-schema.org/draft/2020-12/schema",
                                                "$id": "https://example.com/product.schema.json",
                                                "title": "Schema for Subject",
                                                "description": "A Subject is just a String",
                                                "type": "string"
                                            }
                policy "test"
                permit true
                """;

        assertMatchEvaluation(authzSubscription, document, false);
    }

    @Test
    void when_nonEnforcedSubjectSchemaAndInvalidSubject_then_matching() throws JsonProcessingException {
        var authzSubscription = """
                {
                    "subject"  : 123,
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        var document          = """
                subject     schema {
                                       "$schema": "https://json-schema.org/draft/2020-12/schema",
                                       "$id": "https://example.com/product.schema.json",
                                       "title": "Schema for Subject",
                                       "description": "A Subject is just a String",
                                       "type": "string"
                                   }
                policy "test"
                permit
                """;
        assertMatchEvaluation(authzSubscription, document, true);
    }

    @Test
    void when_enforcedSubjectTwoSchemasSchema_then_bothVersionsMatch() throws JsonProcessingException {
        var document = """
                subject     enforced schema {
                                                "$schema": "https://json-schema.org/draft/2020-12/schema",
                                                "$id": "https://example.com/product.schema.json",
                                                "title": "Schema for Subject",
                                                "description": "A Subject is just a String",
                                                "type": "string"
                                            }
                subject     enforced schema {
                                                "$schema": "https://json-schema.org/draft/2020-12/schema",
                                                "$id": "https://example.com/product.schema.json",
                                                "title": "Schema for Subject",
                                                "description": "A Subject is just a number",
                                                "type": "number"
                                            }
                policy "test"
                permit
                """;

        var authzSubscriptionString = """
                {
                    "subject"  : "willi",
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        var authzSubscriptionNumber = """
                {
                    "subject"  : 123,
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        var authzSubscriptionNull   = """
                {
                    "subject"  : null,
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;

        assertMatchEvaluation(authzSubscriptionString, document, true);
        assertMatchEvaluation(authzSubscriptionNumber, document, true);
        assertMatchEvaluation(authzSubscriptionNull, document, false);
    }

    @SneakyThrows
    private static void assertMatchEvaluation(String subscription, String document, boolean expected) {
        var authzSubscription = MAPPER.readValue(subscription, AuthorizationSubscription.class);
        var sapl              = INTERPRETER.parse(document);
        var match             = sapl.matches().contextWrite(ctx -> AuthorizationContext.setVariables(ctx, Map.of()))
                .contextWrite(ctx -> AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription))
                .contextWrite(ctx -> AuthorizationContext.setAttributeContext(ctx, attributeContext))
                .contextWrite(ctx -> AuthorizationContext.setFunctionContext(ctx, functionContext)).block();

        assertThat(match.getBoolean()).isEqualTo(expected);

        var implicitMatch = MatchingUtil.matches(sapl.getImplicitTargetExpression(), sapl)
                .contextWrite(ctx -> AuthorizationContext.setVariables(ctx, Map.of()))
                .contextWrite(ctx -> AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription))
                .contextWrite(ctx -> AuthorizationContext.setAttributeContext(ctx, attributeContext))
                .contextWrite(ctx -> AuthorizationContext.setFunctionContext(ctx, functionContext)).block();

        assertThat(implicitMatch.getBoolean()).isEqualTo(expected);
    }

    @SneakyThrows
    private static void assertMatchError(String subscription, String document) {
        var authzSubscription = MAPPER.readValue(subscription, AuthorizationSubscription.class);
        var sapl              = INTERPRETER.parse(document);
        var match             = sapl.matches().contextWrite(ctx -> AuthorizationContext.setVariables(ctx, Map.of()))
                .contextWrite(ctx -> AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription))
                .contextWrite(ctx -> AuthorizationContext.setAttributeContext(ctx, attributeContext))
                .contextWrite(ctx -> AuthorizationContext.setFunctionContext(ctx, functionContext)).block();

        assertThat(match.isError()).isTrue();

        var implicitMatch = MatchingUtil.matches(sapl.getImplicitTargetExpression(), sapl)
                .contextWrite(ctx -> AuthorizationContext.setVariables(ctx, Map.of()))
                .contextWrite(ctx -> AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription))
                .contextWrite(ctx -> AuthorizationContext.setAttributeContext(ctx, attributeContext))
                .contextWrite(ctx -> AuthorizationContext.setFunctionContext(ctx, functionContext)).block();

        assertThat(implicitMatch.isError()).isTrue();
    }
}
