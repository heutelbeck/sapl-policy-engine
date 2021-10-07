package io.sapl.hamcrest;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.function.Predicate;

import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

class HasAdviceMatchingTest {

	@Test
	public void test() {
		Predicate<JsonNode> pred = (JsonNode jsonNode) -> jsonNode.has("foo");
		var matcher = Matchers.hasAdviceMatching(pred);

		ObjectMapper mapper = new ObjectMapper();
		ObjectNode advice = mapper.createObjectNode();
		advice.put("foo", "bar");
		ArrayNode adviceArray = mapper.createArrayNode();
		adviceArray.add(advice);
		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(), Optional.of(adviceArray));

		
		assertThat(dec, is(matcher));
	}
	
	@Test
	public void test_neg() {
		Predicate<JsonNode> pred = (JsonNode jsonNode) -> jsonNode.has("xxx");
		var matcher = Matchers.hasAdviceMatching(pred);

		ObjectMapper mapper = new ObjectMapper();
		ObjectNode advice = mapper.createObjectNode();
		advice.put("foo", "bar");
		ArrayNode adviceArray = mapper.createArrayNode();
		adviceArray.add(advice);
		AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(), Optional.of(adviceArray));

		
		assertThat(dec, not(is(matcher)));
	}
	
	@Test
	public void test_nullDecision() {
		Predicate<JsonNode> pred = (JsonNode jsonNode) -> jsonNode.has("foo");
		var matcher = Matchers.hasAdviceMatching(pred);
		assertThat(null, not(is(matcher)));
	}
	
	@Test
	public void test_nullPredicate() {
		assertThrows(NullPointerException.class, () ->  Matchers.hasAdviceMatching(null));
	}
	
	@Test
	public void test_emptyAdvice() {
		Predicate<JsonNode> pred = (JsonNode jsonNode) -> jsonNode.has("foo");
		var matcher = Matchers.hasAdviceMatching(pred);
		assertThat(new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(), Optional.empty()), not(is(matcher)));
	}
	
	@Test
	void testDescriptionForMatcher() {
		Predicate<JsonNode> pred = (JsonNode jsonNode) -> jsonNode.has("foo");
		var sut = Matchers.hasAdviceMatching(pred);
		final StringDescription description = new StringDescription();
		sut.describeTo(description);
		assertThat(description.toString(), is("the decision has an advice matching the predicate"));
	}
}
