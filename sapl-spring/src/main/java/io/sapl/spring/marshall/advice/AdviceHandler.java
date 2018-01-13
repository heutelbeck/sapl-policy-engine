package io.sapl.spring.marshall.advice;

public interface AdviceHandler {

	void handleAdvice(Advice advice);
	
	boolean canHandle (Advice advice);	
}
