package io.sapl.springdatar2dbc.sapl;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Component
@SaplProtected
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD })
public @interface Enforce {

    String subject() default "";

    String action() default "";

    String resource() default "";

    String environment() default "";

    Class<?>[] staticClasses() default Class.class;

}
