package io.sapl.api.interpreter;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import lombok.Value;

@Value
public class Trace {
	Class<?>                 operation;
	List<ExpressionArgument> arguments = new LinkedList<>();
	Instant                  timestamp;

	@Value
	public static class ExpressionArgument {
		String name;
		Val    value;
	}

	public Trace(Class<?> operation) {
		this.operation = operation;
		this.timestamp = Instant.now();
	}

	public Trace(Class<?> operation, Val... arguments) {
		this.operation = operation;
		this.timestamp = Instant.now();
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
		this.timestamp = Instant.now();
		for (var argument : arguments.entrySet()) {
			this.arguments.add(new ExpressionArgument(argument.getKey(), argument.getValue()));
		}
	}

	public Trace(Class<?> operation, ExpressionArgument... arguments) {
		this.operation = operation;
		this.timestamp = Instant.now();
		for (var argument : arguments) {
			this.arguments.add(argument);
		}
	}

	public Trace(Val leftHandValue, Class<?> operation, Val... arguments) {
		this.operation = operation;
		this.timestamp = Instant.now();
		this.arguments.add(new ExpressionArgument("leftHandValue", leftHandValue));
		var i = 0;
		for (var argument : arguments) {
			if (arguments.length == 1)
				this.arguments.add(new ExpressionArgument("argument", argument));
			else
				this.arguments.add(new ExpressionArgument("arguments[" + i++ + "]", argument));
		}
	}

	public String evaluationTree(Val result, String firstLineIndentationString, String follwoingLineIndentationString) {
		var resultString        = result.toString();
		var operationName       = operation.getSimpleName();
		var tree                = firstLineIndentationString + operationName + " -> " + resultString + " " + timestamp
				+ "\n";
		var longestArgumentName = 0;
		for (var argument : arguments) {
			if (argument.getName().length() > longestArgumentName)
				longestArgumentName = argument.getName().length();
		}
		var argumentCount = 0;
		for (var argument : arguments) {
			var indentation    = " ".repeat(operationName.length() + 1);
			var firstLine      = String.format("%s%s|-%-" + longestArgumentName + "s=", follwoingLineIndentationString,
					indentation, argument.getName());
			var isLastArgument = argumentCount++ == arguments.size() - 1;
			var followingLines = follwoingLineIndentationString + indentation + (isLastArgument ? " " : "|")
					+ " ".repeat(3 + longestArgumentName);
			tree += argument.getValue().evaluationTree(firstLine, followingLines);
		}
		return tree;
	}
}
