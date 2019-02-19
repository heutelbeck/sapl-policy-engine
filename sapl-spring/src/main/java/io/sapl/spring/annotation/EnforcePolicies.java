package io.sapl.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The @PdpAuthorize annotation establishes a policy enforcement point (PDP)
 * surrounding the annotated method.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnforcePolicies {

	String subject() default "";

	String action() default "";

	String resource() default "";
	
	boolean resultResource() default false;
}
