package io.sapl.spring.marshall.mapper;

import java.util.Collections;
import java.util.List;
import java.util.Optional;



public class SimpleSaplMapper implements SaplMapper{

	private List<SaplClassMapper> classMappers;
	
	
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
