package io.sapl.spring.constraints.providers;

import java.util.Objects;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;

public class JsonNodeFilteringProvider implements MappingConstraintHandlerProvider<JsonNode> {

	@Override
	public boolean isResponsible(JsonNode constraint)
	{
		if (constraint == null || !constraint.isObject())
			return false;

		var type = constraint.get("type");

		if (Objects.isNull(type) || !type.isTextual())
			return false;

		if (!"filterJson".equals(type.asText()))
			return false;

		return true;
	}

	@Override
	public Class<JsonNode> getSupportedType()
	{
		return JsonNode.class;
	}

	@Override
	public Function<JsonNode, JsonNode> getHandler(JsonNode constraint)
	{
		return node -> node;
	}

}
