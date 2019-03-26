package com.icodici.universa.client;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Errors;
import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node2.ParcelProcessingState;
import com.icodici.universa.node2.network.BasicHttpClientSession;
import com.icodici.universa.node2.network.Client;
import com.icodici.universa.node2.network.ClientError;
import net.sergeych.tools.Do;
import net.sergeych.tools.Reporter;
import net.sergeych.utils.Base64u;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientNetwork {


    public static final Map<String, PublicKey> nodes = new HashMap();
    static {
        try {

            nodes.put("http://node-1-com.utoken.io:8080",new PublicKey(Base64u.decodeCompactString("HggcAQABxAABoJHPqusDZXM/24+65xno0cagoe/0+EvMZ96lPxPtEFVbcgy3D2smKFVNhPFjZoBe+GxqUQEPsb4YY3T8ovUFVUBPYv8dwXGlahnpXiKPJvlTsOrTyK/jUuDNEI64MvtON2+/8EbF02B5jWF4zY276GLfUoSE/h+PLKo49fXHV/uVe73P/4fONqt8NQFKNXU/y2izFHt1TSY7H3PywZqY9o+0y2lwi+cNPHnB1ZCCur5uqgnR81ZEaejNFz1Xa5+B2OOChOT8VFFtu4vVAFxuigX9sPoz2/EXF/9H6/8k7+mxc+BVW8WH6A7yCMGaBowJRo8i6FBchAl88776edXJow==")));
            nodes.put("http://node-2-com.utoken.io:8080",new PublicKey(Base64u.decodeCompactString("HggcAQABxAABlBTsd5kGqDGrjPs3gsefSQdcv4Wbg3+C3Jt3AQo7WLJslHwYVRJOjH103dsbHG3mT+EoRNZ9IVBn9GQRizYtHjFCldLfI+rt7EhuXsdGI59XszyFNbBf0CgW111lqoWlbULf/K3wjUtQ8IEtltmaQ63ZyAg5X2j0gvO2Pl9tt8sS53ejLB5EfuPhoUURqnA8D0H+5FATEADAWQe4mDGuSnwl2QdVGgGkzhyOcKf6CH+s+BrsEW+DTkC6jz6RMFLnYi2t8H9wDm04Dkbd5PLbkGn6Ywr4PGu7h5zSIhJ4UJLBbOmltKlHyHDXZSDP/nINeaQMDpnhE77Z6xVqGARQ4w==")));
            nodes.put("http://node-3-com.utoken.io:8080",new PublicKey(Base64u.decodeCompactString("HggcAQABxAABwlGPo66xnb+U12nCJMt1YsA9DDfUntYPyZQH67zrgjTFiL1ZwXMuynquwNK0QXHseYFR+5WRGvXM0J09O/M50jXytfWEE9j1pZGypYd011O/snbGtKEV+S5hiPjGB/JGtTwiCNipascJZ52EkCiwZFljofK+Px2l15rRK71Un2BSjAsb4Si0MLpxFt1iLdkq0MFXJVHCh4xmWiq3GPYsITsN/diCHblU32AA9hvnTEhvwolfZ3IC1ksohrOTCCl48AROJ5ZAk7YAtd4MHfKTwaXfCjfK3nZbwRL8BMSElLbx9y4NQwZ5MCaY6Q0PNUAO6mN2mOHOmVSbZr9t1F0ETQ==")));
            nodes.put("http://node-4-com.utoken.io:8080",new PublicKey(Base64u.decodeCompactString("HggcAQABxAAB07TWfunHzQT7l2uVKPbHvt/2b1dLHyK2j3WpQlI/NDtEHtFgBhcb0EE2YMM61oJElho8gfPZb6TUgWq8RoyA0EyAoHBPAbDu4+CYPm3HoIgvLDVs8ycgOtmmw6wf6TYJDubsTS+r9AooPED2Js2GIc85PGz9bKkdCcTrkuTsRe6dmCMMY2GL9tkD4ZEYoFRU6iVxQbL7qmRreaKIN2xhRbEfOl5w+4S/cCpJ1dx4ngwiax2iJtQDcJWNpOgqX8UzE8m83xxwAboL3itSUlvvU3Bvfn/LYxWJ2pG4XlW6YKMwblOq1V85GSOsRTee0I2Na1AI/3kusuGzy5ECBbNhlQ==")));
            nodes.put("http://node-5-com.utoken.io:8080",new PublicKey(Base64u.decodeCompactString("HggcAQABxAABtBIXp+RqbpBFtR2oY+bmJJWXytg0gf77WnrntbyQsxhhmS8WnoohBFn5IYzOd1poW4zDJM00MWZmV8VZdCrPp8jt3QCaay4axTT6XEc/JvARJ2KAkO0iQNKgsoPnZUVlO8DO1+X7R+8DkNnlXvld0xexyI0/yKDBGrK8bE0w4oets+4X9oFF1BW9WEvy11+/bc1vS9n8nvX9XCvKh6e+bRF71625fcCzsxap/vdCjDmNOsjjipn8l6BnCS/ygQjfBitqzD9mmu1kRlrQifpbs74UutwFgkGHjq3g7lrrNUikqPjg61XUIm5hfjnHmhJMEuFV5bGmJOVjL/ZYjDxWEQ==")));
            nodes.put("http://node-6-com.utoken.io:8080",new PublicKey(Base64u.decodeCompactString("HggcAQABxAAB2PGl3X3pXussqGHuI59lnxBlJ8njiolzhBrh3nuN6P8AWMWAr9Rlx11B3MrO9dIusWhp9JwGLiGJbKEL9CHIYuPkjpSmhe8wH+Rvik8Lma+haCM8pAUU74NK3mZ0QxmXsV9SYb2phenoQquArIfovR/erMsDvsVIVJlDPzvrqied/0quE5fJ7XvGuYlb7bAho6HOhMFdpGj3dZwYm5EIAYwXHUWeYVse+ZR8DcS5AKnk4Cz140zd2Rl+/uucfrkgiJet+LTx5YhfOKZ9kpjclw682eJnSswlBxJLBsloks8VBJf8dsSGaZxT29cRaRecAoTy9zpMjHPd4VBxpldDew==")));
            nodes.put("http://node-7-com.utoken.io:8080",new PublicKey(Base64u.decodeCompactString("HggcAQABxAAB4VjBNTN0zm9IzgGXAiqukvMphRxO3Eywf0Lqff4JfYDY3Nc3Jg5IjQID/vriCErumnxdQnOL0ci6uJpo6tgDGkvJ4uC7z3Sic+Tsoe6b1vmUSJcNEaBXlGH6kPaNkd2ZlTvBDZ/Iv1v2LwlMDHxK+NrPVHBhEXGguvz7u73fqpC9g3GPpThZwRG+X76YFoeK3P54iwwN127SB6GDV9hFp2XjuEvpLURT0+fnWgqfoFrfW0AMfA/7V6SRb8yF+P45ncOvi3sKJ6owK+/au2dlsHAIE+SCfIOg1iaQBKc3egBlliEZOK/9JSrIBrRxQfqvLCytnbYj7Fed2jmqa9IBaQ==")));
            nodes.put("http://node-8-com.utoken.io:8080",new PublicKey(Base64u.decodeCompactString("HggcAQABxAABnMPCqr/rXOHIYej+/BOniOOkreL0lWbCU7k1GmC3xM9zSSPxL5yZCcmhygttNq6AYoRAjROVAN7ioSF0tORYiqAzzT1GWe55KVFzRsAM6cOJmk0bfYySOiL5qLxjmqnbcCCHOIBkLUjVE8l6TSL284quuQ3iYtNmHdHvC6cb6lzDwqizIsGHaZpyp7A2N52saSUcVaZOPXrqTZP6bRjqOj5KEVqOwR3ZDtXkIUvXZtnrIkI/EoIhjvhv/oThRHdJOExFiPTfA7xqpuOZlK2PazKAwE2+46oaGk1oXEGnx4+XYhXGpArB6hAAhaUJxFxeNnJ2uXKmE6+M38pEQd28Uw==")));
            nodes.put("http://node-9-com.utoken.io:8080",new PublicKey(Base64u.decodeCompactString("HggcAQABxAABt9V2b1nxYGjgBGC03GI1JyG+T6w/QzgGW3GAtVGIQkzMhgpFJAmP+bk6ld4nN4qBjiP0m7oXRpsF/wEPR/BVl8SRtXSEhJ0YdNALg6muK8Qvi7o2mqpOFjlnH9hKbJk/we3je9etwsKcx2iXQN07lOXKr3eihSGIM9nIzxHCN2WAbGEX6v9AIxab1FkqIDdu5jq4DcFhP66SXHyU2U9Ryx9SU3MexvOWkfrQis3wlkGr9Oru4B6yaA3gKt9kekHzqZOjVv/Xp555bcH6OAo05TTgIJ74u1M24DR3LwqUNp50d5DusaxRxoneXTCwoCztNMLn4OXcjdT2Z+ePL+gOjw==")));
            nodes.put("http://node-10-com.utoken.io:8080",new PublicKey(Base64u.decodeCompactString("HggcAQABxAABtu8fYvlDoM4+aNKnGfVegXWatzrlWKEmFhtLIjIfmAuZaXnyQh64hl3VFrOysfuAXSiesKj/ODa5t0LiRe+y/dQi8Zv4X4aNUYQPzOmp6nOvkmfpsASastlKYspNvp2jhrUrNQh0h7Z01R7GGhAuttkDQjAGchCQ/MrqeYMM0PkE30HyRhpb9TGi5WsAH42kQgmFwMKskomTXr7jDc0uCFjkT3lMGwtSOn68qn7Uh0kvwUNdHYAJ0inGPx5O3N82zh6zQCP6Qxn8RWj56iVhk+iTmYjGHH4PiQve4JXFkaqaURt70gb3CynG2JzhEvZ/YB1bFDGBqoTBPjlNcGrBbw==")));

        } catch (EncryptionError encryptionError) {
            encryptionError.printStackTrace();
        }
    }

    static Reporter reporter = CLIMain.getReporter();

    Client client;

    public ClientNetwork(BasicHttpClientSession session) throws IOException {
        this(session, false);
    }

    public ClientNetwork(BasicHttpClientSession session, boolean delayedStart) throws IOException {
        for (int i = 1; i < 10; i++) {
            try {

                String url = Do.sample(nodes.keySet());
                client = new Client(url, CLIMain.getPrivateKey(), session, delayedStart,nodes.get(url));
                break;
            } catch (IOException e) {
                reporter.warning("failed to read network from node: " + e.getMessage() );
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }
        if (client == null)
            throw new IOException("failed to connect to to the universa network");
        reporter.verbose("Read Universa network configuration: " + client.size() + " nodes");
        reporter.message("Network version: " + client.getVersion());
    }

    public ClientNetwork(String nodeUrl, BasicHttpClientSession session) throws IOException {
        this(nodeUrl, session, false);
    }

    public ClientNetwork(String nodeUrl, BasicHttpClientSession session, boolean delayedStart) throws IOException {
        client = new Client(nodeUrl, CLIMain.getPrivateKey(), session, delayedStart);
        if (client == null)
            throw new IOException("failed to connect to to the universa network");
        reporter.verbose("Read Universa network configuration: " + client.size() + " nodes");
        reporter.message("Network version: " + client.getVersion());
    }

    public ClientNetwork(String nodeUrl, PrivateKey privateKey, BasicHttpClientSession session) throws IOException {
        client = new Client(nodeUrl, privateKey, session);
        if (client == null)
            throw new IOException("failed to connect to to the universa network");
        reporter.verbose("Read Universa network configuration: " + client.size() + " nodes");
        reporter.message("Network version: " + client.getVersion());
    }

    public void start(BasicHttpClientSession session) throws IOException {
        client.start(session);
    }

    public ItemResult register(byte[] packedContract) throws ClientError {
        return client.register(packedContract, 0);
    }

    public boolean ping() throws IOException {
        return client.ping();
    }

    /**
     * Register packed binary contract and wait for the consensus.
     *
     * @param packedContract
     * @param millisToWait wait for the consensus as long as specified time, <= 0 means no wait (returns some pending
     *                     state from registering).
     * @return last item status returned by the network
     * @throws ClientError
     */
    public ItemResult register(byte[] packedContract, long millisToWait) throws ClientError {
        return client.register(packedContract, millisToWait);
    }

    /**
     * Register packed binary contract and wait for the consensus.
     *
     * @param packedParcel
     * @param millisToWait wait for the consensus as long as specified time, <= 0 means no wait (returns some pending
     *                     state from registering).
     * @return last item status returned by the network
     * @throws ClientError
     */
    public boolean registerParcel(byte[] packedParcel, long millisToWait) throws ClientError {
        try {
            client.registerParcelWithState(packedParcel, millisToWait);
            return true;
        } catch (ClientError e) {
            if (e.getErrorRecord().getError() == Errors.COMMAND_PENDING)
                return true;
            else
                return false;
        }
    }

    public ItemResult resync(String base64Id) throws ClientError {
        return client.resyncItem(HashId.withDigest(base64Id));
    }

    public ItemResult check(String base64Id) throws ClientError {
        return client.getState(HashId.withDigest(base64Id),reporter);
    }

    public ItemResult check(HashId id) throws ClientError {
        return client.getState(id, reporter);
    }

    public ParcelProcessingState getParcelProcessingState(HashId id) throws ClientError {
        return client.getParcelProcessingState(id);
    }

    public int size() {
        return client.size();
    }

    public BasicHttpClientSession getSession() throws IllegalStateException {
        return client.getSession();
    }

    public int getNodeNumber() {
        return client.getNodeNumber();
    }

    public int checkNetworkState(Reporter reporter) {
        ExecutorService es = Executors.newCachedThreadPool();
        ArrayList<Future<?>> futures = new ArrayList<>();
        AtomicInteger okNodes = new AtomicInteger(0);
        final List<Client.NodeRecord> nodes = client.getNodes();
        for (int nn = 0; nn < client.size(); nn++) {
            final int nodeNumber = nn;
            futures.add(
                    es.submit(() -> {
                        final String url = nodes.get(nodeNumber).url;
                        reporter.verbose("Checking node " + url);
                        for (int i = 0; i < 5; i++) {
                            try {
                                if (client.ping(nodeNumber)) {
                                    okNodes.getAndIncrement();
                                    reporter.verbose("Got an answer from " + url);
                                    return;
                                }
                                reporter.message("retry #" + (i+1) + " on connection failure: " + url);
                                Thread.sleep(200);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        reporter.error(Errors.NOT_READY.name(), url, "failed to connect");
                    })
            );
        }
        futures.forEach(f -> {
            try {
                f.get(4, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                reporter.verbose("node test is timed out");
                f.cancel(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        es.shutdown();
        int n = okNodes.get();
        if (n >= client.size() * 0.12)
            reporter.message("Universa network is active, " + n + " node(s) are reachable");
        else
            reporter.error("NOT_READY", "network", "Universa network is temporarily inaccessible, reachable nodes: " + n);
        return n;
    }

}
