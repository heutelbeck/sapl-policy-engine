package io.sapl.interpreter.trace;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.sapl.api.interpreter.Val;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CapturedAttribute {
	String        name;
	Val           value;
	Optional<Val> leftHand;
	List<Val>     arguments;

	@Override
	public String toString() {
		var sb = new StringBuilder();
		leftHand.ifPresent(val -> sb.append('(').append(val).append(')'));
		sb.append('<').append(name);
		if (!arguments.isEmpty()) {
			sb.append('(');
			sb.append(arguments.stream().map(Val::toString).collect(Collectors.joining(", ")));
			sb.append(')');
		}
		sb.append(">=").append(value);
		return sb.toString();
	}
}
