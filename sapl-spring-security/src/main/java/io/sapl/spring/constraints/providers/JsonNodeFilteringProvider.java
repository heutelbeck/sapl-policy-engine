package io.sapl.spring.constraints.providers;

import java.util.Objects;
import java.util.function.Function;

import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;

import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;

public class JsonNodeFilteringProvider implements MappingConstraintHandlerProvider<JsonNode> {

	private static final String BLACK_SQUARE = "\u2b1b";
	private final Configuration jsonPathConfiguration;

	public JsonNodeFilteringProvider(ObjectMapper objectMapper) {
		jsonPathConfiguration = Configuration.builder()
				.jsonProvider(new JacksonJsonNodeJsonProvider(objectMapper))
				.build();
	}

	@Override
	public boolean isResponsible(JsonNode constraint) {
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
	public Class<JsonNode> getSupportedType() {
		return JsonNode.class;
	}

	@Override
	public Function<JsonNode, JsonNode> getHandler(JsonNode constraint) {
		return original -> {
			var actions = constraint.get("actions");

			if (actions == null)
				return original;

			if (!actions.isArray())
				throw new AccessDeniedException(
						"Error processing node filtering constraint. 'actions' is not an array.");

			var jsonContext = JsonPath.using(jsonPathConfiguration).parse(original);

			for (var action : actions)
				applyAction(jsonContext, action);

			return jsonContext.json();
		};
	}

	private void applyAction(DocumentContext jsonContext, JsonNode action) {
		if (!action.isObject())
			throw new AccessDeniedException(
					"Error processing node filtering constraint. An action in 'actions' is not an object.");

		var path       = pathOfAction(action);
		var actionType = actionTypeOfAction(action);

		if ("delete".equals(actionType)) {
			jsonContext.delete(path);
			return;
		}

		if ("blacken".equals(actionType)) {
			blacken(jsonContext, path, action);
			return;
		}

		throw new AccessDeniedException("Error processing node filtering constraint. Unknown action type.");

	}

	private void blacken(DocumentContext jsonContext, String path, JsonNode action) {
		String replacement;
		var    replacementNode = action.get("replacement");
		if (replacementNode == null)
			replacement = BLACK_SQUARE;
		else if (replacementNode.isTextual())
			replacement = replacementNode.textValue();
		else
			throw new AccessDeniedException(
					"Error processing node filtering constraint. 'replacement' of 'blacken' action is not textual.");
	}

	private static String blacken(String originalString, String replacement, int discloseRight, int discloseLeft) {
		if (discloseLeft + discloseRight >= originalString.length())
			return originalString;

		var result = new StringBuilder();
		if (discloseLeft > 0)
			result.append(originalString, 0, discloseLeft);

		var numberOfReplacedChars = originalString.length() - discloseLeft - discloseRight;
		result.append(String.valueOf(replacement).repeat(Math.max(0, numberOfReplacedChars)));

		if (discloseRight > 0)
			result.append(originalString.substring(discloseLeft + numberOfReplacedChars));

		return result.toString();
	}

	private String actionTypeOfAction(JsonNode action) {
		var actionTypeNode = action.get("type");

		if (actionTypeNode == null)
			throw new AccessDeniedException(
					"Error processing node filtering constraint. An action does not declare its 'type'.");

		if (!actionTypeNode.isTextual())
			throw new AccessDeniedException(
					"Error processing node filtering constraint. An action's 'type' is not textual.");

		return actionTypeNode.textValue().trim().toLowerCase();
	}

	private String pathOfAction(JsonNode action) {
		var pathNode = action.get("path");

		if (pathNode == null)
			throw new AccessDeniedException(
					"Error processing node filtering constraint. An action does not declare a 'path'.");

		if (!pathNode.isTextual())
			throw new AccessDeniedException(
					"Error processing node filtering constraint. An action's 'path' is not textual.");

		return pathNode.textValue();
	}

}
