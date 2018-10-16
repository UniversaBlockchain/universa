package com.icodici.universa.node2.network;

import com.icodici.crypto.HashType;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.network.BasicHTTPService;
import com.icodici.universa.node.network.microhttpd.MicroHTTPDService;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;

import java.io.IOException;
import java.util.Set;

/**
 * Simple example of follower callback server. Use only to receive follower callbacks from Universa nodes.
 */
public class FollowerCallback {
    private PrivateKey callbackKey;
    private int port;
    private String callbackURL;
    private Set<PublicKey> nodeKeys;

    protected BasicHTTPService service;

    public FollowerCallback(PrivateKey callbackKey, int port, String callbackURL) throws IOException {
        this.callbackKey = callbackKey;
        this.port = port;
        this.callbackURL = callbackURL;

        service = new MicroHTTPDService();

        addEndpoint(callbackURL, params -> onCallback(params));

        service.start(port, 32);

        System.out.println("Follower callback server started on port = " + port + " URL = " + callbackURL);
    }

    public void on(String path, BasicHTTPService.Handler handler) {
        service.on(path, handler);
    }

    public void addEndpoint(String path, Endpoint ep) {
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

    public Binder extractParams(BasicHTTPService.Request request) {
        Binder rp = request.getParams();
        BasicHTTPService.FileUpload rd = (BasicHTTPService.FileUpload) rp.get("callbackData");
        if (rd != null) {
            byte[] data = rd.getBytes();
            return Boss.unpack(data);
        }
        return Binder.EMPTY;
    }

    class Result extends Binder {
        private int status = 200;

        public void setStatus(int code) {
            status = code;
        }
    }

    public interface Endpoint {
        public void execute(Binder params, Result result) throws Exception;
    }

    public interface SimpleEndpoint {
        Binder execute(Binder params) throws Exception;
    }

    private Binder onCallback(Binder params) throws IOException {
        byte[] packedItem = params.getBytesOrThrow("data").toArray();
        Contract contract = Contract.fromPackedTransaction(packedItem);

        System.out.println("Follower callback received. Contract: " + contract.getId().toString());

        // check node key
        if (nodeKeys != null) {
            PublicKey nodeKey = new PublicKey(params.getBytesOrThrow("key").toArray());

            byte[] signature = params.getBytesOrThrow("signature").toArray();

            if (!nodeKey.verify(packedItem, signature, HashType.SHA512) || !nodeKeys.stream().anyMatch(n -> n.equals(nodeKey)))
                return Binder.EMPTY;
        }

        // sign receipt
        byte[] receipt = callbackKey.sign(contract.getId().getDigest(), HashType.SHA512);

        return Binder.of("receipt", receipt);
    }

    public void setNetworkNodeKeys(Set<PublicKey> keys) { nodeKeys = keys; }

    public void clearNetworkNodeKeys() { nodeKeys = null; }

    public void shutdown() {
        try {
            service.close();
        } catch (Exception e) {}
    }
}
