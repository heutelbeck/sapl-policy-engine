package io.sapl.spring.marshall.obligation;

public interface ObligationHandler {

	void handleObligation(Obligation obligation) throws ObligationFailed;

	boolean canHandle(Obligation obligation);

}
