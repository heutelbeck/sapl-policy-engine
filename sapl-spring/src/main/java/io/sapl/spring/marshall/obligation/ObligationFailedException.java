package io.sapl.spring.marshall.obligation;

public class ObligationFailedException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6208993128664248985L;

	public ObligationFailedException() {
		super();
	}

	public ObligationFailedException(String message) {
		super(message);
	}
	
	public ObligationFailedException(Exception exception) {
		super(exception);
	}
	
}
