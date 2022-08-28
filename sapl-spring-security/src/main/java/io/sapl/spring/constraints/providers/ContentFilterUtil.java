package io.sapl.spring.constraints.providers;

import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.MapFunction;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ContentFilterUtil {

	private static final String DISCLOSE_LEFT            = "discloseLeft";
	private static final String DISCLOSE_RIGHT           = "discloseRight";
	private static final String REPLACEMENT              = "replacement";
	private static final String REPLACE                  = "replace";
	private static final String BLACKEN                  = "blacken";
	private static final String DELETE                   = "delete";
	private static final String PATH                     = "path";
	private static final String ACTIONS                  = "actions";
	private static final String TYPE                     = "type";
	private static final String BLACK_SQUARE             = "\u2588";
	private static final String UNDEFINED_KEY_S          = "An action does not declare '%s'.";
	private static final String VALUE_NOT_INTEGER_S      = "An action's '%s' is not an integer.";
	private static final String VALUE_NOT_TEXTUAL_S      = "An action's '%s' is not textual.";
	private static final String PATH_NOT_TEXTUAL         = "The contraint indicates a text node to be blackended. However, the node identified by the path is not a text note.";
	private static final String NO_REPLACEMENT_SPECIFIED = "The contraint indicates a text node to be replaced. However, the action does not specify a 'replacement'.";
	private static final String REPLACEMENT_NOT_TEXTUAL  = "'replacement' of 'blacken' action is not textual.";
	private static final String UNKNOWN_ACTION_S         = "Unknown action type: '%s'.";
	private static final String ACTION_NOT_AN_OBJECT     = "An action in 'actions' is not an object.";
	private static final String ACTIONS_NOT_AN_ARRAY     = "'actions' is not an array.";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	public static Function<Object, Object> getHandler(JsonNode constraint, ObjectMapper objectMapper) {
		return original -> {
			var actions               = constraint.get(ACTIONS);
			var jsonPathConfiguration = Configuration.builder()
					.jsonProvider(new JacksonJsonNodeJsonProvider(objectMapper)).build();
			if (actions == null)
				return original;

			if (!actions.isArray())
				throw new IllegalArgumentException(ACTIONS_NOT_AN_ARRAY);

			var originalJsonNode = objectMapper.valueToTree(original);

			var jsonContext = JsonPath.using(jsonPathConfiguration).parse(originalJsonNode);

			for (var action : actions)
				applyAction(jsonContext, action);

			JsonNode modifiedJsonNode = jsonContext.json();

			try {
				return objectMapper.treeToValue(modifiedJsonNode, original.getClass());
			} catch (JsonProcessingException e) {
				throw (new RuntimeException("Error converting modified object to original class type.", e));
			}
		};
	}

	private static void applyAction(DocumentContext jsonContext, JsonNode action) {
		if (!action.isObject())
			throw new IllegalArgumentException(ACTION_NOT_AN_OBJECT);

		var path       = getTextualValueOfActionKey(action, PATH);
		var actionType = getTextualValueOfActionKey(action, TYPE).trim().toLowerCase();

		if (DELETE.equals(actionType)) {
			jsonContext.delete(path);
			return;
		}

		if (BLACKEN.equals(actionType)) {
			blacken(jsonContext, path, action);
			return;
		}

		if (REPLACE.equals(actionType)) {
			replace(jsonContext, path, action);
			return;
		}

		throw new IllegalArgumentException(String.format(UNKNOWN_ACTION_S, actionType));

	}

	private static void replace(DocumentContext jsonContext, String path, JsonNode action) {
		jsonContext.map(path, replaceNode(jsonContext, action));
	}

	private static MapFunction replaceNode(DocumentContext jsonContext, JsonNode action) {
		return (original, configuration) -> {
			if (!action.has(REPLACEMENT))
				throw new IllegalArgumentException(NO_REPLACEMENT_SPECIFIED);

			return action.get(REPLACEMENT);
		};
	}

	private static void blacken(DocumentContext jsonContext, String path, JsonNode action) {
		jsonContext.map(path, blackenNode(jsonContext, action));
	}

	private static MapFunction blackenNode(DocumentContext jsonContext, JsonNode action) {
		return (original, configuration) -> {

			if (!(original instanceof String))
				throw new IllegalArgumentException(PATH_NOT_TEXTUAL);

			var replacementString = determineReplacementString(action);
			var discloseRight     = getIntegerValueOfActionKeyOrDefaultToZero(action, DISCLOSE_RIGHT);
			var discloseLeft      = getIntegerValueOfActionKeyOrDefaultToZero(action, DISCLOSE_LEFT);

			return JSON.textNode(blacken((String) original, replacementString, discloseRight, discloseLeft));
		};
	}

	private static String determineReplacementString(JsonNode action) {
		var replacementNode = action.get(REPLACEMENT);

		if (replacementNode == null)
			return BLACK_SQUARE;

		if (replacementNode.isTextual())
			return replacementNode.textValue();

		throw new IllegalArgumentException(REPLACEMENT_NOT_TEXTUAL);
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

	private static String getTextualValueOfActionKey(JsonNode action, String key) {
		var value = getValueOfActionKey(action, key);

		if (!value.isTextual())
			throw new IllegalArgumentException(String.format(VALUE_NOT_TEXTUAL_S, key));

		return value.textValue();
	}

	private static int getIntegerValueOfActionKeyOrDefaultToZero(JsonNode action, String key) {
		if (!action.has(key))
			return 0;

		var value = action.get(key);

		if (!value.canConvertToInt())
			throw new IllegalArgumentException(String.format(VALUE_NOT_INTEGER_S, key));

		return value.intValue();
	}

	private static JsonNode getValueOfActionKey(JsonNode action, String key) {
		if (!action.hasNonNull(key))
			throw new IllegalArgumentException(String.format(UNDEFINED_KEY_S, key));

		return action.get(key);
	}

}
