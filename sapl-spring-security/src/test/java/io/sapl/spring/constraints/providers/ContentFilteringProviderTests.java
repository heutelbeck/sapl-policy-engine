package io.sapl.spring.constraints.providers;

import static com.spotify.hamcrest.jackson.IsJsonMissing.jsonMissing;
import static com.spotify.hamcrest.jackson.IsJsonObject.jsonObject;
import static com.spotify.hamcrest.jackson.IsJsonText.jsonText;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class ContentFilteringProviderTests {
	private final static ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void when_getSupportedType_then_isObject() {
		var sut = new ContentFilteringProvider(MAPPER);
		assertThat(sut.getSupportedType(), is(Object.class));
	}

	@Test
	void when_constraintIsNull_then_notResponsible() {
		var      sut        = new ContentFilteringProvider(MAPPER);
		JsonNode constraint = null;
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintNonObject_then_notResponsible() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree("123");
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintNoType_then_notResponsible() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree("{ }");
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintTypeNonTextual_then_notResponsible() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree("{ \"type\" : 123 }");
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintWrongType_then_notResponsible() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree("{ \"type\" : \"unrelatedType\" }");
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintTypeCorrect_then_isResponsible() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree("{ \"type\" : \"filterJsonContent\" }");
		assertThat(sut.isResponsible(constraint), is(true));
	}

	@Test
	void when_noActionsSpecified_then_isIdentity() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree("{ \"type\" : \"filterJsonContent\" }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\" }");

		assertThat(handler.apply(original), is(original));
	}

	@Test
	void when_noActionType_then_Error() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"path\" : \"$.key1\"} ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(IllegalArgumentException.class, () -> handler.apply(original));
	}

	@Test
	void when_noActionPath_then_Error() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"delete\" } ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(IllegalArgumentException.class, () -> handler.apply(original));
	}

	@Test
	void when_actionNotAnObject_then_Error() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ 123 ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(IllegalArgumentException.class, () -> handler.apply(original));
	}

	@Test
	void when_actionsNotAnArray_then_Error() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : 123 }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(IllegalArgumentException.class, () -> handler.apply(original));
	}

	@Test
	void when_actionPathNotTextual_then_Error() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"delete\", \"path\" : 123} ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(IllegalArgumentException.class, () -> handler.apply(original));
	}

	@Test
	void when_actionTypeNotTextual_then_Error() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : 123, \"path\" : \"$.key1\"} ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(IllegalArgumentException.class, () -> handler.apply(original));
	}

	@Test
	void when_unknownAction_then_Error() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"unknown action\", \"path\" : \"$.key1\"} ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(IllegalArgumentException.class, () -> handler.apply(original));
	}

	@Test
	void when_blackenHasNonTextualReplacement_then_Error() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"blacken\", \"path\" : \"$.key1\", \"replacement\": 123} ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(IllegalArgumentException.class, () -> handler.apply(original));
	}

	@Test
	void when_blackenTargetsNonTextualNode_then_Error() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"blacken\", \"path\" : \"$.key1\"} ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : 123, \"key2\" : \"value2\" }");

		assertThrows(IllegalArgumentException.class, () -> handler.apply(original));
	}

	@Test
	void when_blackenDiscloseRightNonInteger_then_Error() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"blacken\", \"path\" : \"$.key1\", \"replacement\" : \"X\", \"discloseRight\" : null, \"discloseLeft\" : 1 } ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(IllegalArgumentException.class, () -> handler.apply(original));
	}

	@Test
	void when_blackenDiscloseLeftNonInteger_then_Error() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"blacken\", \"path\" : \"$.key1\", \"replacement\" : \"X\", \"discloseRight\" : 1, \"discloseLeft\" : \"wrongType\" } ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(IllegalArgumentException.class, () -> handler.apply(original));
	}

	@Test
	void when_blacken_then_textIsBlackened() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"blacken\", \"path\" : \"$.key1\", \"replacement\" : \"X\", \"discloseRight\" : 1, \"discloseLeft\" : 1 } ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThat((JsonNode) handler.apply(original), is(jsonObject().where("key1", is(jsonText("vXXXX1")))));
	}

	@Test
	void when_multipleActions_then_allAreExecuted() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"blacken\", \"path\" : \"$.key1\", \"replacement\" : \"X\", \"discloseRight\" : 1, \"discloseLeft\" : 1 }, { \"type\" : \"delete\", \"path\" : \"$.key2\"} ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThat((JsonNode) handler.apply(original),
				is(jsonObject().where("key1", is(jsonText("vXXXX1"))).where("key2", is(jsonMissing()))));
	}

	@Test
	void when_blackenWithDefaultReplacement_then_textIsBlackened()
			throws JsonMappingException,
				JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"blacken\", \"path\" : \"$.key1\", \"discloseRight\" : 1, \"discloseLeft\" : 1 } ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThat((JsonNode) handler.apply(original),
				is(jsonObject().where("key1", is(jsonText("v\u2588\u2588\u2588\u25881")))));
	}

	@Test
	void when_stringToBlackenIsShorterThanDisclosedRange_then_textDoesNotChange()
			throws JsonMappingException,
				JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"blacken\", \"path\" : \"$.key1\", \"discloseRight\" : 2, \"discloseLeft\" : 5 } ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThat((JsonNode) handler.apply(original), is(jsonObject().where("key1", is(jsonText("value1")))));
	}

	@Test
	void when_blackenWithNoParameters_then_textIsBlackenedNoCharsDisclosed()
			throws JsonMappingException,
				JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"blacken\", \"path\" : \"$.key1\" } ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThat((JsonNode) handler.apply(original),
				is(jsonObject().where("key1", is(jsonText("\u2588\u2588\u2588\u2588\u2588\u2588")))));
	}

	@Test
	void when_deleteActionSpecified_then_dataIsRemovedFromJson() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"delete\", \"path\" : \"$.key1\"} ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");
		var expected   = MAPPER.readTree("{ \"key2\" : \"value2\" }");

		assertThat(handler.apply(original), is(expected));
	}

	@Test
	void when_replaceActionHasNoReplacement_then_Error() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"replace\", \"path\" : \"$.key1\" } ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(IllegalArgumentException.class, () -> handler.apply(original));
	}

	@Test
	void when_replaceActionSpecified_then_dataIsReplaced() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"replace\", \"path\" : \"$.key1\", \"replacement\" : { \"I\" : \"am\", \"replaced\" : \"value\"} } ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");
		var expected   = MAPPER
				.readTree("{ \"key1\" : { \"I\" : \"am\", \"replaced\" : \"value\"}, \"key2\" : \"value2\" }");

		assertThat(handler.apply(original), is(expected));
	}

	@Test
	void when_replaceInMap_then_dataIsReplaced() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"replace\", \"path\" : \"$.key1\", \"replacement\" : \"replaced\" } ] }");
		var handler    = sut.getHandler(constraint);
		var original   = Map.of("key1", "value1", "key2", "value2");
		var expected   = Map.of("key1", "replaced", "key2", "value2");
		assertThat(handler.apply(original), is(expected));
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Person {
		String name;
		int    age;
	}

	@Test
	void when_replaceInPoJo_then_dataIsReplaced() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"replace\", \"path\" : \"$.name\", \"replacement\" : \"Alice\" } ] }");
		var handler    = sut.getHandler(constraint);
		var original   = new Person("Bob", 32);
		var expected   = new Person("Alice", 32);
		var actual     = handler.apply(original);
		assertThat(actual, is(expected));
	}

	@Test
	void when_replaceInPoJoAndMarshallingFails_then_Error() throws JsonMappingException, JsonProcessingException {
		var sut        = new ContentFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJsonContent\", \"actions\" : [ { \"type\" : \"replace\", \"path\" : \"$.age\", \"replacement\" : \"Alice\" } ] }");
		var handler    = sut.getHandler(constraint);
		var original   = new Person("Bob", 32);
		assertThrows(RuntimeException.class, () -> handler.apply(original));
	}

}
