package io.sapl.test;

public class SaplTestException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2184089820092089345L;

	
	public SaplTestException() {
		
	}
	
	public SaplTestException(String message) {
		super(message);
	}
	
	public SaplTestException(String message, Exception e) {
		super(message, e);
	}
}
