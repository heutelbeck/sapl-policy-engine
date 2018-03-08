package io.sapl.spring.marshall.mapper;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;


@Slf4j
public class SimpleSaplMapper implements SaplMapper{

	private List<SaplClassMapper> classMappers = new LinkedList<>();
	
	
	@Override
	public void register(SaplClassMapper saplClassMapper) {
		LOGGER.debug("Adding SaplClassMapper: {}", saplClassMapper.getClass().toString());
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

	@Override
	public Object map(Object objectToMap, SaplRequestElement type) {
		LOGGER.debug("-------------------------------------- Mapping {} ---------------------------------------------------", type);
		LOGGER.debug("Entering mapping for {}", objectToMap);
		Optional<SaplClassMapper> classMapper = findClassMapper(objectToMap);
		if (classMapper.isPresent()) {
			return classMapper.get().map(objectToMap, type);
		}
		return objectToMap;
	}

	@Override
	public Optional<SaplClassMapper> findClassMapper(Object objectToMap) {
		LOGGER.trace("Searching ClassMapper in {}", registeredMappers());
		if(objectToMap != null) {
			for(SaplClassMapper cm : registeredMappers()) {
				LOGGER.trace("Mapped Class: {}, Object Class: {}", cm.getMappedClass(), objectToMap.getClass().toString());
			}
			Optional<SaplClassMapper> classMapper = registeredMappers().stream()
					.filter(mapper -> mapper.canMap(objectToMap)).findAny();
			LOGGER.debug("Found ClassMapper: {}", classMapper.isPresent());
			return classMapper;
		}
		LOGGER.debug("Found ClassMapper: false (Object is null)");
		return Optional.empty();
		
	}
	

}
