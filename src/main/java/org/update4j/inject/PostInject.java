package org.update4j.inject;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * A method with this annotation will be called once injection completes.
 * Optionally, if the first parameter type is assignable to the other
 * injectable, it will be passed.
 * 
 * 
 * @author Mordechai Meisels
 *
 */
@Retention(RUNTIME)
@Target(METHOD)
@Documented
public @interface PostInject {

}
