package io.sapl.spring;

import java.util.Collection;

public interface PIPProvider {

	Collection<Class<? extends Object>> getPIPClasses();
}
