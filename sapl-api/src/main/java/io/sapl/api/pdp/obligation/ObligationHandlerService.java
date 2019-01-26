package io.sapl.api.pdp.obligation;

import java.util.List;
import java.util.Optional;

public interface ObligationHandlerService {

	/**
	 * register a new obligationHandler
	 * 
	 * @param obligationHandler
	 *            - the obligation to register
	 */
	void register(ObligationHandler obligationHandler);

	/**
	 * unregister an ObligationHandler
	 * 
	 * @param obligationHandler
	 *            - the obligation to register
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
	 * How to handle the case, where no suitable handler for an obligation is
	 * available
	 */
	default void onNoHandlerAvailable() throws ObligationFailed {
		throw new ObligationFailed("no suitable handler registered in service");
	}

	/**
	 * implements strategy to choose the handler from the registered. <br/>
	 * Should especially cover cases, where more than one handler is suitable for an
	 * obligation
	 * 
	 * @param obligation
	 *            - the obligation
	 * @return Optional of the handler to use for the obligation. Empty, if none
	 *         found
	 */
	default Optional<ObligationHandler> chooseHandler(Obligation obligation) {
		Optional<ObligationHandler> returnHandler = registeredHandlers().stream()
				.filter(handler -> handler.canHandle(obligation)).findAny();
		return returnHandler;
	}

	/**
	 * Handle an Obligations
	 * 
	 * @param obligation
	 *            - the obligation to handle
	 * @throws ObligationFailed
	 *             - maybe thrown by the used {@link ObligationHandler}
	 */
	default void handle(Obligation obligation) throws ObligationFailed {
		Optional<ObligationHandler> handler = chooseHandler(obligation);
		if (handler.isPresent()) {
			handler.get().handleObligation(obligation);
		} else {
			onNoHandlerAvailable();
		}
	}

	/**
	 * Returns {@code true} if a handler suitable for the given obligation
	 * has been registered, {@code false} otherwise.
	 *
	 * @param obligation the obligation to handle
	 * @return {@code true} iff a handler suitable for the given obligation
	 *         has been registered.
	 */
	default boolean couldHandle(Obligation obligation) {
		Optional<ObligationHandler> handler = chooseHandler(obligation);
		return handler.isPresent();
	}

}
