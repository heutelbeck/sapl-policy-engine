package io.sapl.spring;

import java.util.Collection;

/**
 * 
 * @author Daniel T. Schmidt
 *
 */
public interface PIPProvider {

	Collection<Class<? extends Object>> getPIPClasses();
}
