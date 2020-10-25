package io.sapl.server.ce.service.pdpconfiguration;

/**
 * Exception thrown if a provided JSON value is invalid.
 */
public class InvalidJsonException extends Exception {
	public InvalidJsonException(String invalidJson) {
		super(String.format("the provided JSON is invalid: %s", invalidJson));
	}
}
