package io.sapl.api.pip;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Utility interface for providing an explicit service interface for retrieving
 * policy information points.
 * 
 * @author Dominic Heutelbeck
 *
 */
@FunctionalInterface
public interface PolicyInformationPointSupplier extends Supplier<Collection<Object>> {

}
