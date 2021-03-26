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

public class Matchers {
	public static Matcher<Val> val() {
		return new IsVal();
	}

	public static Matcher<Val> val(String text) {
		return new IsVal(jsonText(text));
	}

	public static Matcher<Val> val(int integer) {
		return new IsVal(jsonInt(integer));
	}

	public static Matcher<Val> val(BigDecimal decimal) {
		return new IsVal(jsonBigDecimal(decimal));
	}

	public static Matcher<Val> val(BigInteger bigInt) {
		return new IsVal(jsonBigInteger(bigInt));
	}

	public static Matcher<Val> val(float floatValue) {
		return new IsVal(jsonFloat(floatValue));
	}

	public static Matcher<Val> valNull() {
		return new IsVal(jsonNull());
	}

	public static Matcher<Val> val(boolean bool) {
		return new IsVal(jsonBoolean(bool));
	}

	public static Matcher<Val> valTrue() {
		return val(true);
	}

	public static Matcher<Val> valFalse() {
		return val(false);
	}	
	
	public static Matcher<Val> anyVal() {
		return new IsVal();
	}

	public static Matcher<Val> val(double doubleValue) {
		return new IsVal(jsonDouble(doubleValue));
	}

	public static Matcher<Val> val(long longValue) {
		return new IsVal(jsonLong(longValue));
	}

	public static Matcher<Val> val(Matcher<? super JsonNode> jsonMatcher) {
		return new IsVal(jsonMatcher);
	}
	
	public static Matcher<Val> valError() {
		return new IsValError(is(anything()));
	}

	public static Matcher<Val> valError(String errorMessage) {
		return new IsValError(is(errorMessage));
	}

	public static Matcher<Val> valError(Matcher<? super String> stringMatcher) {
		return new IsValError(stringMatcher);
	}

	public static Matcher<Val> valUndefined() {
		return new IsValUndefined();
	}
	
	public static Matcher<AuthorizationDecision> isPermit() {
		return new IsDecision(Decision.PERMIT);
	}
	
	public static Matcher<AuthorizationDecision> isDeny() {
		return new IsDecision(Decision.DENY);
	}
	
	public static Matcher<AuthorizationDecision> isNotApplicable() {
		return new IsDecision(Decision.NOT_APPLICABLE);
	}
	
	public static Matcher<AuthorizationDecision> isIndeterminate() {
		return new IsDecision(Decision.INDETERMINATE);
	}
	
	public static Matcher<AuthorizationDecision> anyAuthDecision() {
		return new IsDecision();
	}
	
	public static Matcher<AuthorizationDecision> hasObligationMatching(Predicate<? super JsonNode> predicate) {
		return new HasObligationMatching(predicate);
	}
	
	public static Matcher<AuthorizationDecision> hasObligationContainingKeyValue(String key, Matcher<? super JsonNode> value) {
		return new HasObligationContainingKeyValue(key, value);
	}
	
	public static Matcher<AuthorizationDecision> hasObligationContainingKeyValue(String key, String value) {
		return new HasObligationContainingKeyValue(key, jsonText(value));
	}
	
	public static Matcher<AuthorizationDecision> hasObligationContainingKeyValue(String key) {
		return new HasObligationContainingKeyValue(key);
	}
	
	public static Matcher<AuthorizationDecision> hasObligation(ObjectNode node) {
		return new HasObligation(jsonObject(node));
	}
	
	public static Matcher<AuthorizationDecision> hasObligation(Matcher<? super JsonNode> matcher) {
		return new HasObligation(matcher);
	}
		
	public static Matcher<AuthorizationDecision> hasAdviceMatching(Predicate<? super JsonNode> predicate) {
		return new HasAdviceMatching(predicate);
	}

	public static Matcher<AuthorizationDecision> hasAdviceContainingKeyValue(String key, Matcher<? super JsonNode> value) {
		return new HasAdviceContainingKeyValue(key, value);
	}
	
	public static Matcher<AuthorizationDecision> hasAdviceContainingKeyValue(String key, String value) {
		return new HasAdviceContainingKeyValue(key, jsonText(value));
	}
	
	public static Matcher<AuthorizationDecision> hasAdviceContainingKeyValue(String key) {
		return new HasAdviceContainingKeyValue(key);
	}
	
	public static Matcher<AuthorizationDecision> hasAdvice(ObjectNode node) {
		return new HasAdvice(jsonObject(node));
	}
	
	public static Matcher<AuthorizationDecision> hasAdvice(Matcher<? super JsonNode> matcher) {
		return new HasAdvice(matcher);
	}
	
	public static Matcher<AuthorizationDecision> isResource(ObjectNode node) {
		return new IsResource(jsonObject(node));
	}
	
	public static Matcher<AuthorizationDecision> isResource(Matcher<? super JsonNode> matcher) {
		return new IsResource(matcher);
	}
	
	public static Matcher<AuthorizationDecision> isResource() {
		return new IsResource();
	}
	
	public static Matcher<AuthorizationDecision> anyResource() {
		return new IsResource();
	}
	
	public static Matcher<AuthorizationDecision> isResourceMatching(Predicate<? super JsonNode> predicate) {
		return new IsResourceMatching(predicate);
	}
}
