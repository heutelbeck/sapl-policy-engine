package io.sapl.spring.marshall.obligation;

public interface ObligationHandler {

	void handleObligation(Obligation obligation) throws ObligationFailedException;
	
	boolean canHandle(Obligation obligation);
	
}
