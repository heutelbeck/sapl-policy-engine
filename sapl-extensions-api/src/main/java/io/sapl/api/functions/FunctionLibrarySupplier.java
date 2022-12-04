package io.sapl.api.functions;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Utility interface for providing an explicit service interface for retrieving
 * function libraries.
 * 
 * @author Dominic Heutelbeck
 *
 */
public interface FunctionLibrarySupplier extends Supplier<Collection<Object>> {

}
