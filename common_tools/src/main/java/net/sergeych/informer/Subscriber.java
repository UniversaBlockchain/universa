package net.sergeych.informer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods to receive events with the {@link Informer}.
 * <p>
 * Decorate any public method with one argument of some public class, then
 * call {@link Informer#register(Object, boolean)} to receive events that can be casted to the
 * type of the argument. E.g. if the argument type is Object, it will receive all notifications.
 * <p>
 * The return type usually is void. If the method returns boolean, it should return true to break
 * event propagation, meaning that other @Subscriber will not receive this event. This approach
 * should be used with care as the order of invocation of subscriber methods and objects is not
 * specified.
 *
 * Created by sergeych on 14/02/16.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscriber {
}
