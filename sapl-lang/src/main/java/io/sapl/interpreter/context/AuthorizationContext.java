package io.sapl.interpreter.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import lombok.experimental.UtilityClass;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

@UtilityClass
public class AuthorizationContext {
	private static final String          ATTRIBUTE_CTX = "attributeCtx";
	private static final String          FUNCTION_CTX  = "functionCtx";
	private static final String          VARIABLES     = "variables";
	private static final String          IMPORTS       = "imports";
	private static final String          SUBJECT       = "subject";
	private static final String          ACTION        = "action";
	private static final String          RESOURCE      = "resource";
	private static final String          ENVIRONMENT   = "environment";
	private static final String          RELATIVE_NODE = "relativeNode";
	private static final JsonNodeFactory JSON          = JsonNodeFactory.instance;

	public static Map<String, String> getImports(ContextView ctx) {
		return ctx.getOrDefault(IMPORTS, Collections.emptyMap());
	}

	public static Val getRelativeNode(ContextView ctx) {
		return ctx.getOrDefault(RELATIVE_NODE, Val.UNDEFINED);
	}

	public static Context setRelativeNode(Context ctx, Val relativeNode) {
		return ctx.put(RELATIVE_NODE, relativeNode);
	}

	public static AttributeContext getAttributeContext(ContextView ctx) {
		return ctx.get(ATTRIBUTE_CTX);
	}

	public Context setAttributeContext(Context ctx, AttributeContext attributeContext) {
		return ctx.put(ATTRIBUTE_CTX, attributeContext);
	}

	public static Context setVariables(Context ctx, Map<String, JsonNode> environmentrVariables) {
		Map<String, JsonNode> variables = new HashMap<>(ctx.getOrDefault(VARIABLES, new HashMap<>()));
		for (var variable : environmentrVariables.entrySet()) {
			var name = variable.getKey();
			assertVariableNameNotReserved(name);
			variables.put(name, variable.getValue());
		}

		return ctx.put(VARIABLES, variables);
	}

	public Context setVariable(Context ctx, String name, Val value) {
		assertVariableNameNotReserved(name);

		if (value.isError())
			throw new PolicyEvaluationException(value.getMessage());

		Map<String, JsonNode> variables = new HashMap<>(ctx.getOrDefault(VARIABLES, new HashMap<>()));

		if (value.isUndefined())
			variables.remove(name);
		else
			variables.put(name, value.get());
		return ctx.put(VARIABLES, variables);
	}

	private void assertVariableNameNotReserved(String name) {
		if (SUBJECT.equals(name) || RESOURCE.equals(name) || ACTION.equals(name)
				|| ENVIRONMENT.equals(name)) {
			throw new PolicyEvaluationException("cannot overwrite request variable: %s", name);
		}
	}

	public Context setSubscriptionVariables(Context ctx, AuthorizationSubscription authzSubscription) {

		Map<String, JsonNode> variables = new HashMap<>(ctx.getOrDefault(VARIABLES, new HashMap<>()));

		if (authzSubscription.getSubject() != null) {
			variables.put(SUBJECT, authzSubscription.getSubject());
		} else {
			variables.put(SUBJECT, JSON.nullNode());
		}
		if (authzSubscription.getAction() != null) {
			variables.put(ACTION, authzSubscription.getAction());
		} else {
			variables.put(ACTION, JSON.nullNode());
		}
		if (authzSubscription.getResource() != null) {
			variables.put(RESOURCE, authzSubscription.getResource());
		} else {
			variables.put(RESOURCE, JSON.nullNode());
		}
		if (authzSubscription.getEnvironment() != null) {
			variables.put(ENVIRONMENT, authzSubscription.getEnvironment());
		} else {
			variables.put(ENVIRONMENT, JSON.nullNode());
		}
		return ctx.put(VARIABLES, variables);
	}

	public static Map<String, JsonNode> getVariables(ContextView ctx) {
		return ctx.getOrDefault(VARIABLES, new HashMap<>());
	}

	public static Val getVariable(ContextView ctx, String name) {
		var value = getVariables(ctx).get(name);
		if (value == null)
			return Val.UNDEFINED;
		return Val.of(value);
	}

	public static FunctionContext functionContext(ContextView ctx) {
		return ctx.get(FUNCTION_CTX);
	}

	public Context setFunctionContext(Context ctx, FunctionContext functionContext) {
		return ctx.put(FUNCTION_CTX, functionContext);
	}

	public Context setImports(Context ctx, Map<String, String> imports) {
		return ctx.put(IMPORTS, imports);
	}

}
