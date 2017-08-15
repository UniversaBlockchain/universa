package net.sergeych.farcall;

import net.sergeych.utils.Ut;

import java.io.IOException;
import java.net.ProtocolException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * The class that allow connecting two {@link Connector} instances
 * directly.
 *
 * Created by sergeych on 10.04.16.
 */
public class Interconnection {

    private final QueueConnector connectorA;
    private final QueueConnector connectorB;

    private final ArrayBlockingQueue<Object> qa;
    private final ArrayBlockingQueue<Object> qb;

    private boolean bIsClosed = false, aIsClosed = false;

    public QueueConnector getConnectorA() {
        return connectorA;
    }

    public Connector getConnectorB() {
        return connectorB;
    }

    public class QueueConnector implements Connector {
        private final BlockingQueue<Object> input;
        private final BlockingQueue<Object> output;
        private boolean closed = false;
        private boolean _trace = false;
        private long pause = 0;

        public QueueConnector(BlockingQueue<Object> input, BlockingQueue<Object> output) {
            this.input = input;
            this.output = output;
        }

        @Override
        public void send(Map<String, Object> data) throws IOException {
            try {
                if(_trace)
                    System.out.println(">>> "+ Ut.mapToString(data));
                output.put(data);
            } catch (InterruptedException e) {
            }
        }

        @Override
        public Map<String, Object> receive() throws IOException {
            try {
                if( closed )
                    return null;
                Map<String, Object> take = (Map<String, Object>) input.take();
                if( pause > 0 )
                    Thread.sleep(pause);
                if(_trace)
                    System.out.println("<<< "+ Ut.mapToString(take));
                return take;
            }
            catch( ClassCastException ignored) {
                throw new ProtocolException("bad data in channel");
            }
            catch (InterruptedException e) {
                return null;
            }
        }

        @Override
        public void close() {
            closed = true;
            input.offer(null);
        }

        public boolean isClosed() {
            return closed;
        }

        public QueueConnector trace(boolean on) {
            _trace = on;
            return this;
        }

        public QueueConnector pause(long millis) {
            this.pause = millis;
            return this;
        }
    }

    public Interconnection(int capacity) {
        qa = new ArrayBlockingQueue<Object>(capacity);
        qb = new ArrayBlockingQueue<Object>(capacity);
        connectorA = new QueueConnector(qa, qb);
        connectorB = new QueueConnector(qb, qa);
    }

    public void close() {
        connectorA.close();
        connectorB.close();
    }

}
