package io.sapl.spring.marshall.mapper;

import java.util.List;
import java.util.Optional;

/**
 * The SaplMapper is used to marshall a given instance of a class to something
 * that is more suitable to use with Sapl Policies. Therefore it searches a list
 * of given SaplClassMappers and uses the mapper that can map this certain
 * class. If there is no mapper for the class, it will return the same instance
 * it receives. Please note, that any Object will be transformed to a JsonNode
 * before it is evaluated by the Policy Decision Point, so you don't have to
 * care about that.
 *
 */
public interface SaplMapper {

	/**
	 * register a new saplClassMapper
	 * 
	 * @param saplClassMapper
	 *            - the mapper to register
	 */
	void register(SaplClassMapper saplClassMapper);

	/**
	 * unregister an SaplClassMapper
	 * 
	 * @param saplClassMapper
	 *            - the mapper to register
	 */
	void unregister(SaplClassMapper saplClassMapper);

	/**
	 * unregister all SaplClassMappers
	 */
	void unregisterAll();

	/**
	 * @return A list of all registered SaplClassMappers.
	 */
	List<SaplClassMapper> registeredMappers();

	/**
	 * Searches a SaplClassMapper for the objectToMap and uses it to map it.
	 * 
	 * @param objectToMap
	 *            This is the Object you want to be transformed.
	 * @return The transformed Object or the Object itself if no mapper could be
	 *         found.
	 */
	Object map(Object objectToMap, SaplRequestElement type);

	/**
	 * Finds a SaplClassMapper for the given Object if available.
	 * 
	 * @param objectToMap
	 *            To Object that should be mapped.
	 * @return Optional of a SaplClassMapper that can map the objectToMap.
	 */
	Optional<SaplClassMapper> findClassMapper(Object objectToMap);

}
