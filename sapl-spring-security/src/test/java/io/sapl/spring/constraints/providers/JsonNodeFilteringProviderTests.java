package io.sapl.spring.constraints.providers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonNodeFilteringProviderTests {
	private final static ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void when_getSupportedType_then_isJsonNode()
	{
		var sut = new JsonNodeFilteringProvider();
		assertThat(sut.getSupportedType(), is(JsonNode.class));
	}

	@Test
	void when_constraintIsNull_then_notResponsible()
	{
		var      sut        = new JsonNodeFilteringProvider();
		JsonNode constraint = null;
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintNonObject_then_notResponsible() throws JsonMappingException, JsonProcessingException
	{
		var sut        = new JsonNodeFilteringProvider();
		var constraint = MAPPER.readTree("123");
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintNoType_then_notResponsible() throws JsonMappingException, JsonProcessingException
	{
		var sut        = new JsonNodeFilteringProvider();
		var constraint = MAPPER.readTree("{ }");
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintTypeNonTextual_then_notResponsible() throws JsonMappingException, JsonProcessingException
	{
		var sut        = new JsonNodeFilteringProvider();
		var constraint = MAPPER.readTree("{ \"type\" : 123 }");
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintWrongType_then_notResponsible() throws JsonMappingException, JsonProcessingException
	{
		var sut        = new JsonNodeFilteringProvider();
		var constraint = MAPPER.readTree("{ \"type\" : \"unrelatedType\" }");
		assertThat(sut.isResponsible(constraint), is(false));
	}

	@Test
	void when_constraintTypeCorrect_then_isResponsible() throws JsonMappingException, JsonProcessingException
	{
		var sut        = new JsonNodeFilteringProvider();
		var constraint = MAPPER.readTree("{ \"type\" : \"filterJson\" }");
		assertThat(sut.isResponsible(constraint), is(true));
	}

	@Test
	void when_noActionsSpecified_then_isIdentity() throws JsonMappingException, JsonProcessingException
	{
		var sut        = new JsonNodeFilteringProvider();
		var constraint = MAPPER.readTree("{ \"type\" : \"filterJson\" }");
		var handler    = sut.getHandler(constraint);
		var original   = MAPPER.readTree("{ \"key1\" : \"value1\" }");

		assertThat(handler.apply(original), is(original));
	}

}
