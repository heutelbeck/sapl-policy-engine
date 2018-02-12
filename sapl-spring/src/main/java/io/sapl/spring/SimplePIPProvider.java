package io.sapl.spring;

import java.util.Collection;

public class SimplePIPProvider implements PIPProvider {
	
	public SimplePIPProvider (Collection<Class<? extends Object>> pipList) {
		this.pipList = pipList;
	}
	
	private Collection<Class<? extends Object>> pipList;
	
	@Override
	public Collection<Class<? extends Object>> getPIPClasses() {
		return pipList;
	}

}
