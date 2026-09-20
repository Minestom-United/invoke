package dev.minestomUnited.invoke;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface as an invoke service so codegen produces a typed client for it.
 * Every method must take exactly one argument (a record) and return
 * one value or void. Overloaded method names fail the build, because the route path
 * is just the service name plus the method name.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface InvokeService {
}
