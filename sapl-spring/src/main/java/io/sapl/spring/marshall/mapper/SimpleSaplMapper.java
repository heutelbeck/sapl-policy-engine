package io.sapl.spring.marshall.mapper;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;



public class SimpleSaplMapper implements SaplMapper{

	private List<SaplClassMapper> classMappers = new LinkedList<SaplClassMapper>();
	
	
	@Override
	public void register(SaplClassMapper saplClassMapper) {
		classMappers.add(saplClassMapper);
	}

	@Override
	public void unregister(SaplClassMapper saplClassMapper) {
		classMappers.remove(saplClassMapper);
	}

	@Override
	public void unregisterAll() {
		classMappers.clear();
	}
	
	@Override
	public List<SaplClassMapper> registeredMappers() {
		return Collections.unmodifiableList(classMappers);
	}
	

}
