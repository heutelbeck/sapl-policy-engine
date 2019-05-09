package io.sapl.api.pdp;

public class PDPConfigurationException extends Exception {

	private static final long serialVersionUID = 1L;

	public PDPConfigurationException() {
		super();
	}

	public PDPConfigurationException(String message) {
		super(message);
	}

	public PDPConfigurationException(String message, Throwable cause) {
		super(message, cause);
	}

	public PDPConfigurationException(Throwable cause) {
		super(cause);
	}

}
