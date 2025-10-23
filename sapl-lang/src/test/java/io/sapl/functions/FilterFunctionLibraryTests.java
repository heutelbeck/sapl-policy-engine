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
package io.sapl.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.attributes.broker.api.AttributeRepository;
import io.sapl.attributes.broker.impl.CachingAttributeStreamBroker;
import io.sapl.attributes.broker.impl.InMemoryAttributeRepository;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static io.sapl.hamcrest.Matchers.val;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilterFunctionLibraryTests {

    private static final ObjectMapper                 MAPPER               = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final DefaultSAPLInterpreter       INTERPRETER          = new DefaultSAPLInterpreter();
    private static final AttributeRepository          ATTRIBUTE_REPOSITORY = new InMemoryAttributeRepository(
            Clock.systemUTC());
    private static final CachingAttributeStreamBroker ATTRIBUTE_BROKER     = new CachingAttributeStreamBroker(
            ATTRIBUTE_REPOSITORY);
    private static final Map<String, Val>             SYSTEM_VARIABLES     = Collections
            .unmodifiableMap(new HashMap<>());

    private AnnotationFunctionContext functionCtx;

    @BeforeEach
    void setUp() throws InitializationException {
        functionCtx = new AnnotationFunctionContext();
        functionCtx.loadLibrary(FilterFunctionLibrary.class);
    }

    @ParameterizedTest(name = "{index}: {6}")
    @MethodSource("invalidBlackenParameters")
    void blackenInvalidParameters(Val text, Val discloseLeft, Val discloseRight, Val replacement, Val length,
                                  Class<? extends Exception> expectedException, String description) {
        if (length != null) {
            assertThrows(expectedException,
                    () -> FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight, replacement, length));
        } else if (replacement != null) {
            assertThrows(expectedException,
                    () -> FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight, replacement));
        } else if (discloseRight != null) {
            assertThrows(expectedException, () -> FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight));
        } else if (discloseLeft != null) {
            assertThrows(expectedException, () -> FilterFunctionLibrary.blacken(text, discloseLeft));
        } else if (text != null) {
            assertThrows(expectedException, () -> FilterFunctionLibrary.blacken(text));
        } else {
            assertThrows(expectedException, FilterFunctionLibrary::blacken);
        }
    }

    static Stream<Arguments> invalidBlackenParameters() {
        return Stream.of(
                Arguments.of(null, null, null, null, null,
                        IllegalArgumentException.class, "no arguments"),
                Arguments.of(Val.of(9999), null, null, null, null,
                        IllegalArgumentException.class, "non-string text parameter"),
                Arguments.of(Val.of("Cthulhu"), Val.of(2), Val.of(2), Val.of(13), Val.of(2),
                        IllegalArgumentException.class, "non-string replacement parameter"),
                Arguments.of(Val.of("Yog-Sothoth"), Val.of(2), Val.of(2), Val.of("*"), Val.of(-1),
                        IllegalArgumentException.class, "negative length parameter"),
                Arguments.of(Val.of("Azathoth"), Val.of(2), Val.of(2), Val.of("*"), Val.of("eldritch"),
                        IllegalArgumentException.class, "non-numeric length parameter"),
                Arguments.of(Val.of("Nyarlathotep"), Val.of(2), Val.of(-2), null, null,
                        IllegalArgumentException.class, "negative discloseRight parameter"),
                Arguments.of(Val.of("Shub-Niggurath"), Val.of(-2), Val.of(2), null, null,
                        IllegalArgumentException.class, "negative discloseLeft parameter"),
                Arguments.of(Val.of("Hastur"), Val.of(-2), Val.NULL, null, null,
                        IllegalArgumentException.class, "discloseLeft negative, discloseRight null"),
                Arguments.of(Val.of("Dagon"), Val.NULL, Val.of(2), null, null,
                        IllegalArgumentException.class, "discloseLeft null, discloseRight valid"));
    }

    @Test
    void blackenTooManyArguments() {
        val params = new Val[] { Val.of("Necronomicon"), Val.of(2), Val.of(2),
                Val.of("*"), Val.of(2), Val.of(2) };
        assertThrows(IllegalArgumentException.class,
                () -> FilterFunctionLibrary.blacken(params));
    }

    @ParameterizedTest(name = "Redacting {0}: discloseLeft={1}, discloseRight={2}, replacement={3}")
    @CsvSource(delimiter = '|', textBlock = """
            Necronomicon      | 5 | 3 | *   | Necro****con
            Cthulhu           | 2 | 2 | X   | CtXXXhu
            Yog-Sothoth       | 3 | 0 | #   | Yog########
            Azathoth          | 0 | 4 | *   | ****hoth
            R'lyeh            | 1 | 1 | █   | R████h
            Miskatonic        | 0 | 0 | *   | **********
            Innsmouth         | 10| 10| *   | Innsmouth
            Arkham            | 3 | 2 | ░   | Ark░am
            Dunwich           | 4 | 4 | ▓   | Dunwich
            Kadath            | 2 | 1 | ◼   | Ka◼◼◼h
            """)
    void blackenEldritchLocationsAndEntities(String text, int discloseLeft, int discloseRight, String replacement,
                                             String expected) {
        val result = FilterFunctionLibrary.blacken(Val.of(text), Val.of(discloseLeft), Val.of(discloseRight),
                Val.of(replacement));

        assertThat(result, is(val(expected)));
    }

    @ParameterizedTest(name = "{0}: left={1}, right={2}, overrideLength={3} -> {4}")
    @CsvSource(delimiter = '|', textBlock = """
            Necronomicon | 3 | 3 | 2  | Nec**con
            Necronomicon | 3 | 3 | 10 | Nec**********con
            Cthulhu      | 2 | 2 | 10 | Ct**********hu
            Yog-Sothoth  | 3 | 4 | 0  | Yoghoth
            Azathoth     | 4 | 0 | 2  | Azat**
            """)
    void blackenWithLengthOverride(String text, int discloseLeft, int discloseRight, int blackenLength,
                                   String expected) {
        val result = FilterFunctionLibrary.blacken(Val.of(text), Val.of(discloseLeft), Val.of(discloseRight),
                Val.of("*"), Val.of(blackenLength));

        assertThat(result, is(val(expected)));
    }

    @ParameterizedTest(name = "{5}")
    @CsvSource(delimiter = '|', textBlock = """
            Shub-Niggurath | 7 | 7 | * | Shub-Niggurath | fully disclosed name preserved
            Shub-Niggurath | 4 | 4 | * | Shub******rath | partially disclosed name
            Yog-Sothoth    | 0 | 0 | # | ########### | fully blackened name
            """)
    void blackenDisclosureVariations(String text, int discloseLeft, int discloseRight, String replacement,
                                     String expected, String description) {
        val result = FilterFunctionLibrary.blacken(Val.of(text), Val.of(discloseLeft), Val.of(discloseRight),
                Val.of(replacement));

        assertThat(result, is(val(expected)));
    }

    @Test
    void blackenEldritchIncantation() {
        val incantation   = Val.of("Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn");
        val discloseLeft  = Val.of(10);
        val discloseRight = Val.of(10);
        val replacement   = Val.of("*");

        val result = FilterFunctionLibrary.blacken(incantation, discloseLeft, discloseRight, replacement);

        val expected = "Ph'nglui m******************************agl fhtagn";
        assertThat(result, is(val(expected)));
    }

    @Test
    void blackenEdgeCaseEmptyString() {
        val result = FilterFunctionLibrary.blacken(Val.of(""));
        assertThat(result, is(val("")));
    }

    @Test
    void blackenEdgeCaseSingleCharacter() {
        val result = FilterFunctionLibrary.blacken(Val.of("R"), Val.of(0), Val.of(0));
        assertThat(result, is(val("X")));
    }

    @Test
    void blackenEdgeCaseSingleCharacterFullyDisclosed() {
        val result = FilterFunctionLibrary.blacken(Val.of("R"), Val.of(1), Val.of(0));
        assertThat(result, is(val("R")));
    }


    @ParameterizedTest(name = "{5}")
    @MethodSource("specialReplacementScenarios")
    void blackenSpecialReplacements(String text, int left, int right, String replacement,
                                    Integer overrideLength, String expected, String description) {
        Val result;
        if (overrideLength != null) {
            result = FilterFunctionLibrary.blacken(Val.of(text), Val.of(left), Val.of(right),
                    Val.of(replacement), Val.of(overrideLength));
        } else {
            result = FilterFunctionLibrary.blacken(Val.of(text), Val.of(left), Val.of(right),
                    Val.of(replacement));
        }
        assertThat(result, is(val(expected)));
    }

    static Stream<Arguments> specialReplacementScenarios() {
        return Stream.of(
                Arguments.of("Nyarlathotep", 4, 4, "[REDACTED]", null,
                        "Nyar[REDACTED][REDACTED][REDACTED][REDACTED]otep",
                        "multi-character replacement"),
                Arguments.of("古のもの", 1, 1, "█", null,
                        "古██の",
                        "unicode characters"),
                Arguments.of("Nyarlathotep", 4, 4, "", 0,
                        "Nyarotep",
                        "empty replacement with zero length")
        );
    }

    @ParameterizedTest(name = "{3}")
    @MethodSource("basicFunctionTestCases")
    void basicFunctionTests(Val input, Val replacement, Val expected, String description) {
        Val result = switch (description) {
            case "remove returns undefined" -> FilterFunctionLibrary.remove(input);
            case "replace returns replacement value" -> FilterFunctionLibrary.replace(input, replacement);
            case "replace preserves errors" -> FilterFunctionLibrary.replace(input, replacement);
            default -> throw new IllegalStateException("Unexpected test case: " + description);
        };

        assertThat(result, is(expected));
    }

    static Stream<Arguments> basicFunctionTestCases() {
        return Stream.of(
                Arguments.of(Val.of("Elder Sign"), null, Val.UNDEFINED,
                        "remove returns undefined"),
                Arguments.of(Val.NULL, Val.of(13), Val.of(13),
                        "replace returns replacement value"),
                Arguments.of(Val.error("The ritual failed"), Val.of("Safe"), Val.error("The ritual failed"),
                        "replace preserves errors")
        );
    }

    @ParameterizedTest(name = "blackenUtil: {0} with left={1}, right={2}, length={3}")
    @CsvSource(delimiter = '|', nullValues = "null", textBlock = """
            Necronomicon | * | 5 | 3 | null | Necro****con
            Cthulhu      | X | 2 | 2 | 10   | CtXXXXXXXXXXhu
            Yog-Sothoth  | # | 3 | 4 | 0    | Yoghoth
            Azathoth     | * | 4 | 0 | 2    | Azat**
            R'lyeh       | █ | 1 | 1 | null | R████h
            """)
    void blackenUtilDirectTest(String text, String replacement, int left, int right, Integer length, String expected) {
        val result = FilterFunctionLibrary.blackenUtil(text, replacement, right, left, length);
        assertThat(result, is(expected));
    }

    @Test
    void blackenVeryLongEldritchIncantation() {
        val longIncantation = "Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn ".repeat(100);
        val result = FilterFunctionLibrary.blacken(Val.of(longIncantation), Val.of(10), Val.of(10));

        assertThat(result.get().asText().length(), is(longIncantation.length()));
        assertThat(result.get().asText().substring(0, 10), is(longIncantation.substring(0, 10)));
        assertThat(result.get().asText().substring(result.get().asText().length() - 10),
                is(longIncantation.substring(longIncantation.length() - 10)));
    }

    @Test
    void blackenInPolicyProtectsEldritchKnowledge() throws JsonProcessingException {
        val authzSubscription = MAPPER.readValue("""
                {
                  "resource" : {
                                 "artifacts" : [ "Necronomicon", "Elder Sign" ],
                                 "secretName"  : "Abdul Alhazred"
                               }
                }""", AuthorizationSubscription.class);

        val policyDefinition = """
                policy "protect_eldritch_names"
                permit
                transform resource |- {
                                        @.secretName : filter.blacken(1)
                                      }""";

        val expectedResource = MAPPER.readValue("""
                {
                  "artifacts" : [ "Necronomicon", "Elder Sign" ],
                  "secretName"  : "AXXXXXXXXXXXXX"
                }""", JsonNode.class);

        val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
                Optional.empty(), Optional.empty());

        StepVerifier
                .create(INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_BROKER, functionCtx,
                        SYSTEM_VARIABLES))
                .assertNext(authzDecision -> assertThat(authzDecision, is(expectedAuthzDecision))).verifyComplete();
    }

    @Test
    void replaceInPolicyCensorsEldritchTruths() throws JsonProcessingException {
        val authzSubscription = MAPPER.readValue("""
                {
                  "resource" : {
                                 "rituals" : [ null, true ],
                                 "summoningWords"  : "Ia! Ia! Cthulhu fhtagn!"
                               }
                }""", AuthorizationSubscription.class);

        val policyDefinition = """
                policy "censor_summonings"
                permit
                transform resource |- {
                                        @.rituals[1] : filter.replace("REDACTED"),
                                        @.summoningWords : filter.replace(null)
                                      }""";

        val expectedResource = MAPPER.readValue("""
                {
                  "rituals" : [ null, "REDACTED" ],
                  "summoningWords"  : null
                }""", JsonNode.class);

        val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
                Optional.empty(), Optional.empty());

        StepVerifier
                .create(INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_BROKER, functionCtx,
                        SYSTEM_VARIABLES))
                .assertNext(authzDecision -> assertThat(authzDecision, is(expectedAuthzDecision))).verifyComplete();
    }

    @Test
    void removeInPolicyErasesEldritchKnowledge() throws JsonProcessingException {
        val authzSubscription = MAPPER.readValue("""
                {
                  "resource" : {
                                 "locations" : [ "Arkham", "Innsmouth" ],
                                 "forbiddenKnowledge"  : "The King in Yellow"
                               }
                }""", AuthorizationSubscription.class);

        val policyDefinition = """
                policy "erase_forbidden_knowledge"
                permit
                transform resource |- {
                                        @.forbiddenKnowledge : filter.remove
                                      }""";

        val expectedResource = MAPPER.readValue("""
                {
                  "locations" : [ "Arkham", "Innsmouth" ]
                }""", JsonNode.class);

        val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
                Optional.empty(), Optional.empty());

        StepVerifier
                .create(INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_BROKER, functionCtx,
                        SYSTEM_VARIABLES))
                .assertNext(authzDecision -> assertThat(authzDecision, is(expectedAuthzDecision))).verifyComplete();
    }

    @Test
    void blackenAppliesRecursivelyToNestedStructures() throws JsonProcessingException {
        val authzSubscription = MAPPER.readValue("""
                {
                  "resource" : {
                    "investigators": [
                      {
                        "name": "Dr. Armitage",
                        "affiliation": "Miskatonic University",
                        "ssn": "123-45-6789"
                      },
                      {
                        "name": "Professor Wilmarth",
                        "affiliation": "Miskatonic University",
                        "ssn": "987-65-4321"
                      }
                    ]
                  }
                }""", AuthorizationSubscription.class);

        val policyDefinition = """
                policy "mask_all_investigator_ssns"
                permit
                transform resource |- {
                    @..ssn : filter.blacken(0, 4)
                }""";

        val expectedResource = MAPPER.readValue("""
                {
                  "investigators": [
                    {
                      "name": "Dr. Armitage",
                      "affiliation": "Miskatonic University",
                      "ssn": "XXXXXXX6789"
                    },
                    {
                      "name": "Professor Wilmarth",
                      "affiliation": "Miskatonic University",
                      "ssn": "XXXXXXX4321"
                    }
                  ]
                }""", JsonNode.class);

        val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
                Optional.empty(), Optional.empty());

        StepVerifier
                .create(INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_BROKER, functionCtx,
                        SYSTEM_VARIABLES))
                .assertNext(authzDecision -> assertThat(authzDecision, is(expectedAuthzDecision))).verifyComplete();
    }
}