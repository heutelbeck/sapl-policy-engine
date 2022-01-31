package io.sapl.spring.constraints.providers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

public class JsonNodeFilteringProviderTests {
	private final static ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void when_getSupportedType_then_isJsonNode() {
		var sut = new JsonNodeFilteringProvider(MAPPER);
		assertThat(sut.getSupportedType(), is(JsonNode.class));
	}

	@Test
	void when_constraintIsNull_then_notResponsible() {
		var      sut        = new JsonNodeFilteringProvider(MAPPER);
		JsonNode constraint = null;
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintNonObject_then_notResponsible() throws JsonMappingException, JsonProcessingException {
		var sut        = new JsonNodeFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree("123");
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintNoType_then_notResponsible() throws JsonMappingException, JsonProcessingException {
		var sut        = new JsonNodeFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree("{ }");
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintTypeNonTextual_then_notResponsible() throws JsonMappingException, JsonProcessingException {
		var sut        = new JsonNodeFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree("{ \"type\" : 123 }");
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintWrongType_then_notResponsible() throws JsonMappingException, JsonProcessingException {
		var sut        = new JsonNodeFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree("{ \"type\" : \"unrelatedType\" }");
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintTypeCorrect_then_isResponsible() throws JsonMappingException, JsonProcessingException {
		var sut        = new JsonNodeFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree("{ \"type\" : \"filterJson\" }");
		assertThat(sut.isResponsible(constraint), is(true));
	}

	@Test
	void when_noActionsSpecified_then_isIdentity() throws JsonMappingException, JsonProcessingException {
		var sut        = new JsonNodeFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree("{ \"type\" : \"filterJson\" }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\" }");

		assertThat(handler.apply(original), is(original));
	}

	@Test
	void when_noActionType_then_AccessDenied() throws JsonMappingException, JsonProcessingException {
		var sut        = new JsonNodeFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJson\", \"actions\" : [ { \"path\" : \"$.key1\"} ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(AccessDeniedException.class, () -> handler.apply(original));
	}

	@Test
	void when_noActionPath_then_AccessDenied() throws JsonMappingException, JsonProcessingException {
		var sut        = new JsonNodeFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJson\", \"actions\" : [ { \"type\" : \"delete\" } ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(AccessDeniedException.class, () -> handler.apply(original));
	}

	@Test
	void when_actionNotAnObject_then_AccessDenied() throws JsonMappingException, JsonProcessingException {
		var sut        = new JsonNodeFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJson\", \"actions\" : [ 123 ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(AccessDeniedException.class, () -> handler.apply(original));
	}

	@Test
	void when_actionsNotAnArray_then_AccessDenied() throws JsonMappingException, JsonProcessingException {
		var sut        = new JsonNodeFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJson\", \"actions\" : 123 }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(AccessDeniedException.class, () -> handler.apply(original));
	}

	@Test
	void when_actionPathNotTextual_then_AccessDenied() throws JsonMappingException, JsonProcessingException {
		var sut        = new JsonNodeFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJson\", \"actions\" : [ { \"type\" : \"delete\", \"path\" : 123} ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(AccessDeniedException.class, () -> handler.apply(original));
	}

	@Test
	void when_actionTypeNotTextual_then_AccessDenied() throws JsonMappingException, JsonProcessingException {
		var sut        = new JsonNodeFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJson\", \"actions\" : [ { \"type\" : 123, \"path\" : \"$.key1\"} ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(AccessDeniedException.class, () -> handler.apply(original));
	}

	@Test
	void when_unknownAction_then_AccessDenied() throws JsonMappingException, JsonProcessingException {
		var sut        = new JsonNodeFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJson\", \"actions\" : [ { \"type\" : \"unknown action\", \"path\" : \"$.key1\"} ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(AccessDeniedException.class, () -> handler.apply(original));
	}

	@Test
	void when_blackenHasNonTextualReplacement_then_AccessDenied() throws JsonMappingException, JsonProcessingException {
		var sut        = new JsonNodeFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJson\", \"actions\" : [ { \"type\" : \"blacken\", \"path\" : \"$.key1\", \"replacement\": 123} ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");

		assertThrows(AccessDeniedException.class, () -> handler.apply(original));
	}
	@Test
	void when_deleteActionSpecified_then_dataIsRemovedFromJson() throws JsonMappingException, JsonProcessingException {
		var sut        = new JsonNodeFilteringProvider(MAPPER);
		var constraint = MAPPER.readTree(
				"{ \"type\" : \"filterJson\", \"actions\" : [ { \"type\" : \"delete\", \"path\" : \"$.key1\"} ] }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\", \"key2\" : \"value2\" }");
		var expected   = MAPPER.readTree("{ \"key2\" : \"value2\" }");

		assertThat(handler.apply(original), is(expected));
	}

	// @Test
	void experimentJsonPath() {
		var             jsonDataSourceString        = "{\r\n"
				+ "    \"tool\": \r\n"
				+ "    {\r\n"
				+ "        \"jsonpath\": \r\n"
				+ "        {\r\n"
				+ "            \"creator\": \r\n"
				+ "            {\r\n"
				+ "                \"name\": \"Jayway Inc.\",\r\n"
				+ "                \"location\": \r\n"
				+ "                [\r\n"
				+ "                    \"Malmo\",\r\n"
				+ "                    \"San Francisco\",\r\n"
				+ "                    \"Helsingborg\"\r\n"
				+ "                ]\r\n"
				+ "            }\r\n"
				+ "        }\r\n"
				+ "    },\r\n"
				+ "\r\n"
				+ "    \"book\": \r\n"
				+ "    [\r\n"
				+ "        {\r\n"
				+ "            \"title\": \"Beginning JSON\",\r\n"
				+ "            \"price\": 49.99\r\n"
				+ "        },\r\n"
				+ "\r\n"
				+ "        {\r\n"
				+ "            \"title\": \"JSON at Work\",\r\n"
				+ "            \"price\": 29.99\r\n"
				+ "        }\r\n"
				+ "    ]\r\n"
				+ "}";
		String          jsonpathCreatorNamePath     = "$['tool']['jsonpath']['creator']['name']";
		String          jsonpathCreatorLocationPath = "$['tool']['jsonpath']['creator']['location'][*]";
		DocumentContext jsonContext                 = JsonPath.parse(jsonDataSourceString);
		String          jsonpathCreatorName         = jsonContext.read(jsonpathCreatorNamePath);
		List<String>    jsonpathCreatorLocation     = jsonContext.read(jsonpathCreatorLocationPath);
		System.out.println("jsonpathCreatorName     -> " + jsonpathCreatorName);
		System.out.println("jsonpathCreatorLocation -> " + jsonpathCreatorLocation);
		System.out.println("before                  -> " + jsonContext.json());
		// jsonContext.delete(jsonpathCreatorNamePath);
		final JsonNodeFactory json = JsonNodeFactory.instance;
		jsonContext.map(jsonpathCreatorNamePath, (value, config) -> json.textNode(value + "XXX"));

		System.out.println("after                   -> " + jsonContext.json());
	}
}
