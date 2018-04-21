package io.sapl.spring;

import java.util.Collection;

@FunctionalInterface
public interface PIPProvider {
	Collection<Class<? extends Object>> getPIPClasses();
}
