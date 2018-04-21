package io.sapl.spring.marshall.obligation;

public class ObligationFailed extends Exception {

	private static final long serialVersionUID = 6208993128664248985L;

	public ObligationFailed() {
		super();
	}

	public ObligationFailed(String message) {
		super(message);
	}

	public ObligationFailed(Exception exception) {
		super(exception);
	}

}
