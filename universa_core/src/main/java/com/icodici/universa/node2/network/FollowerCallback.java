package com.icodici.universa.node2.network;

import com.icodici.crypto.HashType;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.network.BasicHTTPService;
import com.icodici.universa.node.network.microhttpd.MicroHTTPDService;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;

import java.io.IOException;
import java.util.Set;

/**
 * Simple example of follower callback server. Use only to receive follower callbacks from Universa network.
 * Follower callback server receives follower callback from Universa nodes, checks signature according to the key set of
 * public keys of Universa nodes (optional), and sends a receipt to the node with a signed callback key.
 */
public class FollowerCallback {
    private PrivateKey callbackKey;
    private int port;
    private String callbackURL;
    private Set<PublicKey> nodeKeys;

    protected BasicHTTPService service;

    /**
     * Initialize and start follower callback server.
     *
     * @param callbackKey is {@link PrivateKey} on which the follower callback server signs the response node
     * @param port for listening by follower callback server
     * @param callbackURL is URL to where callbacks are sent from node
     */
    public FollowerCallback(PrivateKey callbackKey, int port, String callbackURL) throws IOException {
        this.callbackKey = callbackKey;
        this.port = port;
        this.callbackURL = callbackURL;

        service = new MicroHTTPDService();

        addEndpoint(callbackURL, params -> onCallback(params));

        service.start(port, 32);

        System.out.println("Follower callback server started on port = " + port + " URL = " + callbackURL);
    }

    private void on(String path, BasicHTTPService.Handler handler) {
        service.on(path, handler);
    }

    private void addEndpoint(String path, Endpoint ep) {
        on(path, (request, response) -> {
            Binder result;
            try {
                Result epResult = new Result();
                ep.execute(extractParams(request), epResult);
                result = epResult;
            } catch (Exception e) {
                result = new Binder();
            }
            response.setBody(Boss.pack(result));
        });
    }

    void addEndpoint(String path, SimpleEndpoint sep) {
        addEndpoint(path, (params, result) -> {
            result.putAll(sep.execute(params));
        });
    }

    private Binder extractParams(BasicHTTPService.Request request) {
        Binder rp = request.getParams();
        BasicHTTPService.FileUpload rd = (BasicHTTPService.FileUpload) rp.get("callbackData");
        if (rd != null) {
            byte[] data = rd.getBytes();
            return Boss.unpack(data);
        }
        return Binder.EMPTY;
    }

    public interface Endpoint {
        void execute(Binder params, Result result) throws Exception;
    }

    public interface SimpleEndpoint {
        Binder execute(Binder params) throws Exception;
    }

    private Binder onCallback(Binder params) throws IOException {
        String event = params.getString("event");
        byte[] packedData;
        HashId id;

        if (event.equals("new")) {
            packedData = params.getBytesOrThrow("data").toArray();
            Contract contract = Contract.fromPackedTransaction(packedData);
            id = contract.getId();
        } else if (event.equals("revoke")) {
            packedData = params.getBytesOrThrow("id").toArray();
            id = HashId.withDigest(packedData);
        } else
            return Binder.EMPTY;

        if (event.equals("new"))
            System.out.println("Follower callback received. Contract: " + id.toString());
        else
            System.out.println("Follower callback received. Revoking ID: " + id.toString());

        // check node key
        if (nodeKeys != null) {
            PublicKey nodeKey = new PublicKey(params.getBytesOrThrow("key").toArray());

            byte[] signature = params.getBytesOrThrow("signature").toArray();

            if (!nodeKey.verify(packedData, signature, HashType.SHA512) || !nodeKeys.stream().anyMatch(n -> n.equals(nodeKey)))
                return Binder.EMPTY;
        }

        // sign receipt
        byte[] receipt = callbackKey.sign(id.getDigest(), HashType.SHA512);

        if (event.equals("new"))
            System.out.println("Follower callback processed. Contract: " + id.toString());
        else
            System.out.println("Follower callback processed. Revoking ID: " + id.toString());

        return Binder.of("receipt", receipt);
    }

    /**
     * Set public keys of Universa nodes for checking callback on follower callback server.
     * If checking is successful, follower callback server sends a receipt to the node with a signed callback key.
     *
     * This checking is optional, and may be passed if Universa nodes keys not set or reset by
     * {@link FollowerCallback#clearNetworkNodeKeys}.
     *
     * @param keys is set of {@link PublicKey} Universa nodes
     */
    public void setNetworkNodeKeys(Set<PublicKey> keys) { nodeKeys = keys; }

    /**
     * Reset public keys of Universa nodes for disable checking callback on follower callback server.
     */
    public void clearNetworkNodeKeys() { nodeKeys = null; }

    /**
     * Shutdown the follower callback server.
     */
    public void shutdown() {
        try {
            service.close();

            System.out.println("Follower callback server stopped on port = " + port + " URL = " + callbackURL);
        } catch (Exception e) {}
    }

    class Result extends Binder {
        private int status = 200;

        public void setStatus(int code) {
            status = code;
        }
    }
}
