package io.sapl.spring.method.pre;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The @PreEnforce annotation establishes a policy enforcement point (PEP) before the
 * invocation of the annotated method.
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface PreEnforce {

	String subject() default "";

	String action() default "";

	String resource() default "";

	String environment() default "";

}
