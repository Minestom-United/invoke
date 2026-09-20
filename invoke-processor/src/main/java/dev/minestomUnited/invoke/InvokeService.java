package dev.minestomUnited.invoke;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compile-time copy of the service marker for the annotation processor. Lives
 * here so the processor jar needs no runtime dependency; the canonical
 * annotation is dev.minestomUnited.invoke.InvokeService in invoke-runtime.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface InvokeService {
}
