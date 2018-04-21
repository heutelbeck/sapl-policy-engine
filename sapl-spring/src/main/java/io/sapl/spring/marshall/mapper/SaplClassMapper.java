package io.sapl.spring.marshall.mapper;

/**
 * Each SaplClassMapper can map Objects of a certain class. The SaplClassMappers
 * should be registered to the SaplMapper.
 */
public interface SaplClassMapper {

	/**
	 * Receives an Object and maps it to something else.
	 * 
	 * @param objectToMap
	 *            The object that should be mapped.
	 * @return The mapped Object.
	 */
	Object map(Object objectToMap, SaplRequestElement element);

	/**
	 * Checks if an Object can be mapped by this mapper.
	 * 
	 * @param objectToMap
	 *            The object that should be mapped.
	 * @return True, if the Object can be mapped, or false if not.
	 */
	default boolean canMap(Object objectToMap) {
		return getMappedClass().isInstance(objectToMap);
	}

	/**
	 * Provides the class this mapper can map.
	 * 
	 * @return The String name of the class that this Mapper can map.
	 */
	Class<?> getMappedClass();

}
