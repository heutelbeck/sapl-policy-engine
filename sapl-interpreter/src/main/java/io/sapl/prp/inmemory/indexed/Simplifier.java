package io.sapl.prp.inmemory.indexed;

public interface Simplifier<T extends Simplifiable> {

	T reduce(final T obj);
}
