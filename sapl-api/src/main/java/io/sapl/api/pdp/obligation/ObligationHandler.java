package io.sapl.api.pdp.obligation;

public interface ObligationHandler {

	void handleObligation(Obligation obligation) throws ObligationFailed;

	boolean canHandle(Obligation obligation);

}
