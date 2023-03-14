package io.sapl.api.interpreter;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Value;

@Value
public class Trace {
	Class<?>                 operation;
	List<ExpressionArgument> arguments = new LinkedList<>();

	@Value
	public static class ExpressionArgument {
		String name;
		Val    value;
	}

	public Trace(Class<?> operation) {
		this.operation = operation;
	}

	public Trace(Class<?> operation, Val... arguments) {
		this.operation = operation;
		var i = 0;
		for (var argument : arguments) {
			if (arguments.length == 1)
				this.arguments.add(new ExpressionArgument("argument", argument));
			else
				this.arguments.add(new ExpressionArgument("arguments[" + i++ + "]", argument));
		}
	}

	public Trace(Class<?> operation, Map<String, Val> arguments) {
		this.operation = operation;
		for (var argument : arguments.entrySet()) {
			this.arguments.add(new ExpressionArgument(argument.getKey(), argument.getValue()));
		}
	}

	public Trace(Class<?> operation, ExpressionArgument... arguments) {
		this.operation = operation;
		for (var argument : arguments) {
			this.arguments.add(argument);
		}
	}

	public Trace(Val leftHandValue, Class<?> operation, Val... arguments) {
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
		var jsonTrace = Val.JSON.objectNode();
		jsonTrace.set("operator", Val.JSON.textNode(operation.getSimpleName()));
		if (!arguments.isEmpty()) {
			var args = Val.JSON.objectNode();
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
