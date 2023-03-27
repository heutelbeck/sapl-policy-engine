package io.sapl.api.interpreter;

import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import lombok.Value;

@Value
public class Trace {
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	Class<?>                 operation;
	List<ExpressionArgument> arguments = new LinkedList<>();

	public Trace(Class<?> operation) {
		this.operation = operation;
	}

	public Trace(Class<?> operation, Traced... arguments) {
		this.operation = operation;
		var i = 0;
		for (var argument : arguments) {
			if (arguments.length == 1)
				this.arguments.add(new ExpressionArgument("argument", argument));
			else
				this.arguments.add(new ExpressionArgument("arguments[" + i++ + "]", argument));
		}
	}

	public Trace(Class<?> operation, Map<String, Traced> arguments) {
		this.operation = operation;
		for (var argument : arguments.entrySet()) {
			this.arguments.add(new ExpressionArgument(argument.getKey(), argument.getValue()));
		}
	}

	public Trace(Class<?> operation, ExpressionArgument... arguments) {
		this.operation = operation;
		this.arguments.addAll(Arrays.asList(arguments));
	}

	public Trace(Traced leftHandValue, Class<?> operation, Traced... arguments) {
		this.operation = operation;
		this.arguments.add(new ExpressionArgument("leftHandValue", leftHandValue));
		var i = 0;
		for (var argument : arguments) {
			if (arguments.length == 1)
				this.arguments.add(new ExpressionArgument("argument", argument));
			else
				this.arguments.add(new ExpressionArgument("arguments[" + i++ + "]", argument));
		}
	}

	public JsonNode getTrace() {
		var jsonTrace = JSON.objectNode();
		jsonTrace.set("operator", JSON.textNode(operation.getSimpleName()));
		if (!arguments.isEmpty()) {
			var args = JSON.objectNode();
			for (var argument : arguments)
				args.set(argument.getName(), argument.getValue().getTrace());
			jsonTrace.set("arguments", args);
		}
		return jsonTrace;
	}

	public List<ExpressionArgument> getArguments() {
		return Collections.unmodifiableList(arguments);
	}
}
