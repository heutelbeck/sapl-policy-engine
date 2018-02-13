package io.sapl.spring;

import java.util.Collection;
import java.util.Collections;

public class SimplePIPProvider implements PIPProvider {
	
	private Collection<Class<? extends Object>> pipList;
	
	public SimplePIPProvider (Collection<Class<? extends Object>> pipList) {
		this.pipList = Collections.unmodifiableCollection(pipList);
	}
	
	
	@Override
	public Collection<Class<? extends Object>> getPIPClasses() {
		return pipList;
	}

}
