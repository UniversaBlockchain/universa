package net.sergeych.informer;

/**
 * The event that is automatically posted if no one hash received some other posted event
 *
 * Created by sergeych on 14/02/16.
 */
public class LostEvent {
    private Object source;

    public LostEvent(Object sourceEvent) {
        source = sourceEvent;
    }

    public Object getSource() {
        return source;
    }
}
