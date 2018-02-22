package io.sapl.spring.marshall.mapper;

public interface SaplClassMapper {

	
	Object map (Object objectToMap);
	
	boolean canMap(Object objectToMap);

}
