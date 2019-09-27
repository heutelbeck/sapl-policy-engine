package io.sapl.interpreter;

/**
 * The reactive version of a non reactive method returning void would return a Flux of
 * {@link java.lang.Void}. Since the only instance of the type java.lang.Void is
 * {@code null} which cannot be used for Flux items (e.g. Flux.just(null) is not
 * possible), another type representing void must be used.
 *
 * This is the purpose of this class. It provides a non null instance representing a void
 * result.
 */
public class Void {

	public static final Void INSTANCE = new Void();

}
