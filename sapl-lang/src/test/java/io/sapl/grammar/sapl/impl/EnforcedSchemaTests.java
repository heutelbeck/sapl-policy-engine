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
package io.sapl.grammar.sapl.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import io.sapl.functions.ArrayFunctionLibrary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.attributes.broker.impl.CachingAttributeStreamBroker;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.SchemaValidationLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.grammar.sapl.impl.util.MatchingUtil;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.SimpleFunctionLibrary;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import lombok.SneakyThrows;

class EnforcedSchemaTests {
    private static final ObjectMapper           MAPPER      = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
    private static CachingAttributeStreamBroker attributeStreamBroker;
    private static AnnotationFunctionContext    functionContext;

    @BeforeAll
    static void beforeAll() throws InitializationException {
        attributeStreamBroker = new CachingAttributeStreamBroker();
        functionContext       = new AnnotationFunctionContext();
        functionContext.loadLibrary(SimpleFunctionLibrary.class);
        functionContext.loadLibrary(SchemaValidationLibrary.class);
        functionContext.loadLibrary(FilterFunctionLibrary.class);
        functionContext.loadLibrary(StandardFunctionLibrary.class);
        functionContext.loadLibrary(ArrayFunctionLibrary.class);
    }

    @Test
    void when_noEnforcementAndNoTarget_then_matches() {
        final var authzSubscription = """
                {
                    "subject"  : "willi",
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        final var document          = """
                policy "test"
                permit
                """;

        assertMatchEvaluation(authzSubscription, document, true);
    }

    @Test
    void when_noEnforcementButWithTarget_then_matches() {
        final var authzSubscription = """
                {
                    "subject"  : "willi",
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        final var document          = """
                policy "test"
                permit true
                """;

        assertMatchEvaluation(authzSubscription, document, true);
    }

    @Test
    void when_enforcedSubjectSchemaAndValidSubject_then_matches() {
        final var authzSubscription = """
                {
                    "subject"  : "willi",
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        final var document          = """
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
    void when_enforcedSubjectSchemaAndValidSubjectButTargetNoMatch_then_noMatch() {
        final var authzSubscription = """
                {
                    "subject"  : "willi",
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        final var document          = """
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
    void when_schemaError_then_error() {
        final var authzSubscription = """
                {
                    "subject"  : "willi",
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        final var document          = """
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
    void when_elementError_then_error() {
        final var authzSubscription = """
                {
                    "subject"  : "willi",
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        final var document          = """
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
    void when_enforcedSubjectAndActionSchemaAndValidSubjectAndAction_then_bothMustMatch() {
        final var bothMatchSubscription      = """
                {
                    "subject"  : "willi",
                    "action"   : 123,
                    "resource" : "ice cream"
                }
                """;
        final var actionNoMatchSubscription  = """
                {
                    "subject"  : "willi",
                    "action"   : true,
                    "resource" : "ice cream"
                }
                """;
        final var subjectNoMatchSubscription = """
                {
                    "subject"  : 987,
                    "action"   : 123,
                    "resource" : "ice cream"
                }
                """;
        final var document                   = """
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
    void when_enforcedSubjectSchemaAndInvalidSubject_then_notMatching() {
        final var authzSubscription = """
                {
                    "subject"  : 123,
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        final var document          = """
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
    void when_nonEnforcedSubjectSchemaAndInvalidSubject_then_matching() {
        final var authzSubscription = """
                {
                    "subject"  : 123,
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        final var document          = """
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
    void when_enforcedSubjectTwoSchemasSchema_then_bothVersionsMatch() {
        final var document = """
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

        final var authzSubscriptionString = """
                {
                    "subject"  : "willi",
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        final var authzSubscriptionNumber = """
                {
                    "subject"  : 123,
                    "action"   : "eat",
                    "resource" : "ice cream"
                }
                """;
        final var authzSubscriptionNull   = """
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
        final var authzSubscription = MAPPER.readValue(subscription, AuthorizationSubscription.class);
        final var sapl              = INTERPRETER.parse(document);
        final var match             = sapl.matches()
                .contextWrite(ctx -> AuthorizationContext.setVariables(ctx, Map.of()))
                .contextWrite(ctx -> AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription))
                .contextWrite(ctx -> AuthorizationContext.setAttributeStreamBroker(ctx, attributeStreamBroker))
                .contextWrite(ctx -> AuthorizationContext.setFunctionContext(ctx, functionContext)).block();

        assertThat(match).isNotNull();
        assertThat(match.getBoolean()).isEqualTo(expected);

        final var implicitMatch = MatchingUtil.matches(sapl.getImplicitTargetExpression(), sapl)
                .contextWrite(ctx -> AuthorizationContext.setVariables(ctx, Map.of()))
                .contextWrite(ctx -> AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription))
                .contextWrite(ctx -> AuthorizationContext.setAttributeStreamBroker(ctx, attributeStreamBroker))
                .contextWrite(ctx -> AuthorizationContext.setFunctionContext(ctx, functionContext)).block();

        assertThat(implicitMatch).isNotNull();
        assertThat(implicitMatch.getBoolean()).isEqualTo(expected);
    }

    @SneakyThrows
    private static void assertMatchError(String subscription, String document) {
        final var authzSubscription = MAPPER.readValue(subscription, AuthorizationSubscription.class);
        final var sapl              = INTERPRETER.parse(document);
        final var match             = sapl.matches()
                .contextWrite(ctx -> AuthorizationContext.setVariables(ctx, Map.of()))
                .contextWrite(ctx -> AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription))
                .contextWrite(ctx -> AuthorizationContext.setAttributeStreamBroker(ctx, attributeStreamBroker))
                .contextWrite(ctx -> AuthorizationContext.setFunctionContext(ctx, functionContext)).block();

        assertThat(match.isError()).isTrue();

        final var implicitMatch = MatchingUtil.matches(sapl.getImplicitTargetExpression(), sapl)
                .contextWrite(ctx -> AuthorizationContext.setVariables(ctx, Map.of()))
                .contextWrite(ctx -> AuthorizationContext.setSubscriptionVariables(ctx, authzSubscription))
                .contextWrite(ctx -> AuthorizationContext.setAttributeStreamBroker(ctx, attributeStreamBroker))
                .contextWrite(ctx -> AuthorizationContext.setFunctionContext(ctx, functionContext)).block();

        assertThat(implicitMatch.isError()).isTrue();
    }
}
