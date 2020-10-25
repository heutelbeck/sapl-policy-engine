package io.sapl.server.ce.service.pdpconfiguration;

import io.sapl.server.ce.model.pdpconfiguration.Variable;
import lombok.NonNull;

/**
 * {@link Exception} for a duplicated name of a {@link Variable}.
 */
public class DuplicatedVariableNameException extends Exception {
	public DuplicatedVariableNameException(@NonNull String duplicatedName) {
		super(String.format("variable name \"%s\" is already used by another variable", duplicatedName));
	}
}
