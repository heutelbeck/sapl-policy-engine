package io.sapl.spring.marshall.advice;

import java.util.List;
import java.util.Optional;

public interface AdviceHandlerService {
	
	/**
	 * register a new AdviceHandler
	 * @param AdviceHandler - the AdviceHandler to register
	 */
	void register(AdviceHandler adviceHandler);
	
	/**
	 * unregister an AdviceHandler
	 * @param AdviceHandler - the AdviceHandler to register
	 */
	void unregister(AdviceHandler adviceHandler);
	
	/**
	 * unregister all AdviceHandlers
	 */
	void unregisterAll();
	
	/**
	 * 
	 * @return List of all registered handlers
	 */
	List<AdviceHandler> registeredHandlers();
	
	
	/**
	 * implements strategy to choose the handler from the registered. <br/>
	 * Should especially cover cases, where more than one handler is
	 * suitable for an advice
	 * @param advice - the advice
	 * @return Optional of the handler to use for the advice. Empty, if none found
	 */
	default Optional<AdviceHandler> chooseHandler(Advice advice){
		Optional<AdviceHandler> returnHandler = registeredHandlers().stream()
			.filter(handler -> handler.canHandle(advice))
			.findAny();
		return returnHandler;
	}
	
	/**
	 * Handle an Advice
	 * @param advice - the advice to handle 
	 */
	default void handle(Advice advice) {
		Optional<AdviceHandler> handler = chooseHandler(advice);
		if(handler.isPresent()){
			handler.get().handleAdvice(advice);
		}
	}

}
