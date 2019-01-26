package io.sapl.api.pdp.advice;

public interface AdviceHandler {

	void handleAdvice(Advice advice);

	boolean canHandle(Advice advice);
}
