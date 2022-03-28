package io.sapl.vaadin;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.vaadin.flow.component.UI;

import reactor.core.publisher.Mono;

/**
 * This class is a container class for the following constraint handler types:
 *   - Runnable handlers
 *   - Consumer handlers: UI is passed
 *   - Vaadin function handlers: UI and current decision is passed
 */
public class VaadinConstraintHandlerBundle {
    public final List<Function<UI, Mono<Boolean>>> vaadinFunctionHandlerList = new LinkedList<>();
    public final List<Consumer<UI>> consumerHandlerList = new LinkedList<>();
    public final List<Runnable> runnableHandlerList = new LinkedList<>();
}
