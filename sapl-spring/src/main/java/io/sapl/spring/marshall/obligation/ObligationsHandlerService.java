package io.sapl.spring.marshall.obligation;

import java.util.List;
import java.util.Optional;


public interface ObligationsHandlerService {
	
	/**
	 * register a new obligationHandler
	 * @param obligationHandler - the obligation to register
	 */
	void register(ObligationHandler obligationHandler);
	
	/**
	 * unregister an ObligationHandler
	 * @param obligationHandler - the obligation to register
	 */
	void unregister(ObligationHandler obligationHandler);
	
	/**
	 * unregister all ObligationHandlers
	 */
	void unregisterAll();
	
	/**
	 * 
	 * @return List of all registered handlers
	 */
	List<ObligationHandler> registeredHandlers();
	
	/**
	 * How to handle the case, where no suitable
	 * handler for an obligation is available
	 */
	default void onNoHandlerAvailable() throws ObligationFailedException{
		throw new ObligationFailedException("no suitable handler registered in service");
	}
	
	/**
	 * implements strategy to choose the handler from the registered. <br/>
	 * Should especially cover cases, where more than one handler is
	 * suitable for an obligation
	 * @param obligation - the obligation
	 * @return Optional of the handler to use for the obligation. Empty, if none found
	 */
	default Optional<ObligationHandler> chooseHandler(Obligation obligation){
		Optional<ObligationHandler> returnHandler = registeredHandlers().stream()
			.filter(handler -> handler.canHandle(obligation))
			.findAny();
		return returnHandler;
	}
	
	/**
	 * Handle an Obligations
	 * @param obligation - the obligation to handle
	 * @throws ObligationFailedException - maybe thrown by the used {@link ObligationHandler} 
	 */
	default void handle(Obligation obligation) throws ObligationFailedException {
		Optional<ObligationHandler> handler = chooseHandler(obligation);
		if(handler.isPresent()){
			handler.get().handleObligation(obligation);
		}else {
			onNoHandlerAvailable();
		}
	}
	
}
