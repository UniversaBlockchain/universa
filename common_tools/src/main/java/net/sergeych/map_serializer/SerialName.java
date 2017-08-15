package net.sergeych.map_serializer;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation that allows specify alias name for class and/or
 * for field, see {@link MapSerializer}. Note that if you use
 * it with classes, you should invoke {@link MapSerializer#registerPackage(String)}
 * prior to deserialization.
 * 
 * @author sergeych
 *
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface SerialName {
	String name();
}
