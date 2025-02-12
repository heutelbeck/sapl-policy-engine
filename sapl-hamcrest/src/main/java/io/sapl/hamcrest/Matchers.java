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
package io.sapl.hamcrest;

import static com.spotify.hamcrest.jackson.IsJsonBoolean.jsonBoolean;
import static com.spotify.hamcrest.jackson.IsJsonNull.jsonNull;
import static com.spotify.hamcrest.jackson.IsJsonNumber.jsonBigDecimal;
import static com.spotify.hamcrest.jackson.IsJsonNumber.jsonBigInteger;
import static com.spotify.hamcrest.jackson.IsJsonNumber.jsonDouble;
import static com.spotify.hamcrest.jackson.IsJsonNumber.jsonFloat;
import static com.spotify.hamcrest.jackson.IsJsonNumber.jsonInt;
import static com.spotify.hamcrest.jackson.IsJsonNumber.jsonLong;
import static com.spotify.hamcrest.jackson.IsJsonText.jsonText;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonObject;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsAnything.anything;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.Predicate;

import org.hamcrest.Matcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import lombok.experimental.UtilityClass;

/**
 * Utility class exposing static access to the various SAPL matchers.
 */
@UtilityClass
public class Matchers {

    /**
     * Tests for a Val object.
     *
     * @return a matcher checking if an object is a Val.
     */
    public static Matcher<Val> val() {
        return new IsVal();
    }

    /**
     * Tests for a Val object containing a JSON text.
     *
     * @param text the JSON text to check for.
     * @return a matcher checking if an object is a Val containing a JSON text.
     */
    public static Matcher<Val> val(String text) {
        return new IsVal(jsonText(text));
    }

    /**
     * Tests for a Val object containing a JSON number with an integer.
     *
     * @param integer the number to check for
     * @return a matcher checking if an object is a Val object containing a JSON
     * number with an integer.
     */
    public static Matcher<Val> val(int integer) {
        return new IsVal(jsonInt(integer));
    }

    /**
     * Tests for a Val object containing a JSON number with a BigDecimal.
     *
     * @param decimal the number to check for
     * @return a matcher checking if an object is a Val object containing a JSON
     * number with an BigDecimal.
     */
    public static Matcher<Val> val(BigDecimal decimal) {
        return new IsVal(jsonBigDecimal(decimal));
    }

    /**
     * Tests for a Val object containing a JSON number with a BigInteger.
     *
     * @param bigInt the number to check for
     * @return a matcher checking if an object is a Val object containing a JSON
     * number with an BigInteger.
     */
    public static Matcher<Val> val(BigInteger bigInt) {
        return new IsVal(jsonBigInteger(bigInt));
    }

    /**
     * Tests for a Val object containing a JSON number with a Float.
     *
     * @param floatValue the number to check for
     * @return a matcher checking if an object is a Val object containing a JSON
     * number with a Float.
     */
    public static Matcher<Val> val(float floatValue) {
        return new IsVal(jsonFloat(floatValue));
    }

    /**
     * Tests for a Val object containing a JSON null.
     *
     * @return a matcher checking if an object is a Val object containing a JSON
     * null.
     */
    public static Matcher<Val> valNull() {
        return new IsVal(jsonNull());
    }

    /**
     * Tests for a Val object containing a JSON number with a Boolean.
     *
     * @param bool the number to check for
     * @return a matcher checking if an object is a Val object containing a JSON
     * Boolean value.
     */
    public static Matcher<Val> val(boolean bool) {
        return new IsVal(jsonBoolean(bool));
    }

    /**
     * Tests for a Val object containing a JSON Boolean true.
     *
     * @return a matcher checking if an object is a Val object containing a JSON
     * Boolean true.
     */
    public static Matcher<Val> valTrue() {
        return val(true);
    }

    /**
     * Tests for a Val object containing a JSON Boolean false.
     *
     * @return a matcher checking if an object is a Val object containing a JSON
     * Boolean false.
     */
    public static Matcher<Val> valFalse() {
        return val(false);
    }

    /**
     * Tests for a Val object.
     *
     * @return a matcher checking if an object is a Val.
     */
    public static Matcher<Val> anyVal() {
        return new IsVal();
    }

    /**
     * Tests for a Val object containing a JSON number with a double.
     *
     * @param doubleValue the number to check for
     * @return a matcher checking if an object is a Val object containing a JSON
     * number with a double.
     */
    public static Matcher<Val> val(double doubleValue) {
        return new IsVal(jsonDouble(doubleValue));
    }

    /**
     * Tests for a Val object containing a JSON number with a long.
     *
     * @param longValue the number to check for
     * @return a matcher checking if an object is a Val object containing a JSON
     * number with a long.
     */
    public static Matcher<Val> val(long longValue) {
        return new IsVal(jsonLong(longValue));
    }

    /**
     * Tests for a Val object containing a JSON matching a JsonNode matcher.
     *
     * @param jsonMatcher a {@code Matcher<? super JsonNode>}
     * @return a matcher checking if an object is a Val matching a JsonNode matcher.
     */
    public static Matcher<Val> val(Matcher<? super JsonNode> jsonMatcher) {
        return new IsVal(jsonMatcher);
    }

    /**
     * Tests for a Val error with any text.
     *
     * @return a matcher for a Val error with any text.
     */
    public static Matcher<Val> valError() {
        return new IsValError(is(anything()));
    }

    /**
     * Tests for a Val error with a specific text.
     *
     * @param errorMessage expected error message
     * @return a matcher for a Val error with any text.
     */
    public static Matcher<Val> valError(String errorMessage) {
        return new IsValError(is(errorMessage));
    }

    /**
     * Tests for a Val error with a text matching a String matcher.
     *
     * @param stringMatcher a string matcher
     * @return a matcher for a Val error a text matching a String matcher.
     */
    public static Matcher<Val> valError(Matcher<? super String> stringMatcher) {
        return new IsValError(stringMatcher);
    }

    /**
     * Matcher to check if a Val is undefined
     *
     * @return Matcher to check if a Val is undefined
     */
    public static Matcher<Val> valUndefined() {
        return new IsValUndefined();
    }

    /**
     * An AuthorizationDecision matcher, checking if the decision is PERMIT.
     *
     * @return An AuthorizationDecision matcher, checking if the decision is PERMIT.
     */
    public static Matcher<AuthorizationDecision> isPermit() {
        return new IsDecision(Decision.PERMIT);
    }

    /**
     * An AuthorizationDecision matcher, checking if the decision is DENY.
     *
     * @return An AuthorizationDecision matcher, checking if the decision is DENY.
     */
    public static Matcher<AuthorizationDecision> isDeny() {
        return new IsDecision(Decision.DENY);
    }

    /**
     * An AuthorizationDecision matcher, checking if the decision is NOT_APPLICABLE.
     *
     * @return An AuthorizationDecision matcher, checking if the decision is
     * NOT_APPLICABLE.
     */
    public static Matcher<AuthorizationDecision> isNotApplicable() {
        return new IsDecision(Decision.NOT_APPLICABLE);
    }

    /**
     * An AuthorizationDecision matcher, checking if the decision is INDETERMINATE.
     *
     * @return An AuthorizationDecision matcher, checking if the decision is
     * INDETERMINATE.
     */
    public static Matcher<AuthorizationDecision> isIndeterminate() {
        return new IsDecision(Decision.INDETERMINATE);
    }

    /**
     * A matcher checking if the object is an AuthorizationDecision.
     *
     * @return A matcher checking if the object is an AuthorizationDecision
     */
    public static Matcher<AuthorizationDecision> anyDecision() {
        return new IsDecision();
    }

    /**
     * A AuthorizationDecision matcher checking for the existence of an obligation
     * satisfying the predicate.
     *
     * @param predicate a Predicate<JsonNode>
     * @return A AuthorizationDecision matcher checking for the existence of an
     * obligation satisfying the predicate.
     */
    public static Matcher<AuthorizationDecision> hasObligationMatching(Predicate<? super JsonNode> predicate) {
        return new HasObligationMatching(predicate);
    }

    /**
     * A AuthorizationDecision matcher checking for the existence of an obligation
     * containing a key-value pair.
     *
     * @param key a key.
     * @param value a value matcher.
     * @return A AuthorizationDecision matcher checking for the existence of an
     * obligation containing a key-value pair.
     */
    public static Matcher<AuthorizationDecision> hasObligationContainingKeyValue(String key,
            Matcher<? super JsonNode> value) {
        return new HasObligationContainingKeyValue(key, value);
    }

    /**
     * A AuthorizationDecision matcher checking for the existence of an obligation
     * containing a key-value pair.
     *
     * @param key a key.
     * @param value a value.
     * @return A AuthorizationDecision matcher checking for the existence of an
     * obligation containing a key-value pair.
     */
    public static Matcher<AuthorizationDecision> hasObligationContainingKeyValue(String key, String value) {
        return new HasObligationContainingKeyValue(key, jsonText(value));
    }

    /**
     * A AuthorizationDecision matcher checking for the existence of an obligation
     * containing a key.
     *
     * @param key a key.
     * @return A AuthorizationDecision matcher checking for the existence of an
     * obligation containing a key pair.
     */
    public static Matcher<AuthorizationDecision> hasObligationContainingKeyValue(String key) {
        return new HasObligationContainingKeyValue(key);
    }

    /**
     * A AuthorizationDecision matcher checking for the existence of any obligation
     *
     * @return A AuthorizationDecision matcher checking for the existence of any
     * obligation
     */
    public static Matcher<AuthorizationDecision> hasObligation() {
        return new HasObligation();
    }

    /**
     * A AuthorizationDecision matcher checking for the existence of an obligation
     * matching a specific ObjectNode.
     *
     * @param node an ObjectNode
     * @return A AuthorizationDecision matcher checking for the existence of an
     * obligation a specific ObjectNode.
     */
    public static Matcher<AuthorizationDecision> hasObligation(ObjectNode node) {
        return new HasObligation(jsonObject(node));
    }

    /**
     * A AuthorizationDecision matcher checking for the existence of an obligation
     * matching a specific JSON text.
     *
     * @param value a String
     * @return A AuthorizationDecision matcher checking for the existence of an
     * obligation a specific JSON text.
     */
    public static Matcher<AuthorizationDecision> hasObligation(String value) {
        return new HasObligation(jsonText(value));
    }

    /**
     * A AuthorizationDecision matcher checking for the existence of an obligation
     * matching a matcher.
     *
     * @param matcher a JsonNode matcher
     * @return A AuthorizationDecision matcher checking for the existence of an
     * obligation matching a matcher.
     */
    public static Matcher<AuthorizationDecision> hasObligation(Matcher<? super JsonNode> matcher) {
        return new HasObligation(matcher);
    }

    /**
     * A AuthorizationDecision matcher checking for the existence of any advice
     *
     * @return A AuthorizationDecision matcher checking for the existence of any
     * advice
     */
    public static Matcher<AuthorizationDecision> hasAdvice() {
        return new HasAdvice();
    }

    /**
     * A AuthorizationDecision matcher checking for the existence of an advice
     * satisfying the predicate.
     *
     * @param predicate a Predicate<JsonNode>
     * @return A AuthorizationDecision matcher checking for the existence of an
     * advice satisfying the predicate.
     */
    public static Matcher<AuthorizationDecision> hasAdviceMatching(Predicate<? super JsonNode> predicate) {
        return new HasAdviceMatching(predicate);
    }

    /**
     * A AuthorizationDecision matcher checking for the existence of an advice
     * containing a key-value pair.
     *
     * @param key a key.
     * @param value a value matcher.
     * @return A AuthorizationDecision matcher checking for the existence of an
     * advice containing a key-value pair.
     */
    public static Matcher<AuthorizationDecision> hasAdviceContainingKeyValue(String key,
            Matcher<? super JsonNode> value) {
        return new HasAdviceContainingKeyValue(key, value);
    }

    /**
     * A AuthorizationDecision matcher checking for the existence of an advice
     * containing a key-value pair.
     *
     * @param key a key.
     * @param value a value.
     * @return A AuthorizationDecision matcher checking for the existence of an
     * advice containing a key-value pair.
     */
    public static Matcher<AuthorizationDecision> hasAdviceContainingKeyValue(String key, String value) {
        return new HasAdviceContainingKeyValue(key, jsonText(value));
    }

    /**
     * A AuthorizationDecision matcher checking for the existence of an obligation
     * advice a key.
     *
     * @param key a key.
     * @return A AuthorizationDecision matcher checking for the existence of an
     * advice containing a key pair.
     */
    public static Matcher<AuthorizationDecision> hasAdviceContainingKeyValue(String key) {
        return new HasAdviceContainingKeyValue(key);
    }

    /**
     * A AuthorizationDecision matcher checking for the existence of an advice
     * matching a specific ObjectNode.
     *
     * @param node an ObjectNode
     * @return A AuthorizationDecision matcher checking for the existence of an
     * advice a specific ObjectNode.
     */
    public static Matcher<AuthorizationDecision> hasAdvice(ObjectNode node) {
        return new HasAdvice(jsonObject(node));
    }

    /**
     * A AuthorizationDecision matcher checking for the existence of an advice
     * matching a specific JSON text.
     *
     * @param value a String
     * @return A AuthorizationDecision matcher checking for the existence of an
     * advice a specific JSON text.
     */
    public static Matcher<AuthorizationDecision> hasAdvice(String value) {
        return new HasAdvice(jsonText(value));
    }

    /**
     * A AuthorizationDecision matcher checking for the existence of an advice
     * matching a matcher.
     *
     * @param matcher a JsonNode matcher
     * @return A AuthorizationDecision matcher checking for the existence of an
     * advice matching a matcher.
     */
    public static Matcher<AuthorizationDecision> hasAdvice(Matcher<? super JsonNode> matcher) {
        return new HasAdvice(matcher);
    }

    /**
     * A AuthorizationDecision Matcher checking for the presence of a resource
     * object matching an ObjectNode.
     *
     * @param node an ObjectNode
     * @return A AuthorizationDecision Matcher checking for the presence of a
     * resource object matching an ObjectNode.
     */
    public static Matcher<AuthorizationDecision> hasResource(ObjectNode node) {
        return new HasResource(jsonObject(node));
    }

    /**
     * A AuthorizationDecision Matcher checking for the presence of a resource
     * object matching a JsonNode matcher.
     *
     * @param matcher a matcher
     * @return A AuthorizationDecision Matcher checking for the presence of a
     * resource object matching a JsonNode matcher.
     */
    public static Matcher<AuthorizationDecision> hasResource(Matcher<? super JsonNode> matcher) {
        return new HasResource(matcher);
    }

    /**
     * A AuthorizationDecision Matcher checking for the presence of a resource
     * object.
     *
     * @return A AuthorizationDecision Matcher checking for the presence of a
     * resource object.
     */
    public static Matcher<AuthorizationDecision> hasResource() {
        return new HasResource();
    }

    /**
     * A AuthorizationDecision Matcher checking for the presence of a resource
     * object matching a predicate.
     *
     * @param predicate a JsonNode predicate
     * @return A AuthorizationDecision Matcher checking for the presence of a
     * resource object matching a predicate.
     */
    public static Matcher<AuthorizationDecision> hasResourceMatching(Predicate<? super JsonNode> predicate) {
        return new HasResourceMatching(predicate);
    }

}
