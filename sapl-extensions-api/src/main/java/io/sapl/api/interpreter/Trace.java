/*
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
