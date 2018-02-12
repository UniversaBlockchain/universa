package com.icodici.universa.node2.network;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.crypto.SymmetricKey;
import net.sergeych.tools.Binder;

public class BasicHttpClientSession {

    private String connectMessage;
    private PrivateKey privateKey;
    private SymmetricKey sessionKey;
    private long sessionId;
    private PublicKey nodePublicKey;

    public BasicHttpClientSession()  {
    }

    public Binder asBinder()  {
        return Binder.fromKeysValues(
                "connectMessage", getConnectMessage(),
//                    "privateKey", privateKey.pack(),
                "sessionKey", getSessionKey().pack(),
                "sessionId", getSessionId()
//                    "nodePublicKey", nodePublicKey.pack()
        );
    }

    public static BasicHttpClientSession reconstructSession(Binder binder)  {
        BasicHttpClientSession restoringSession = new BasicHttpClientSession();
        restoringSession.setConnectMessage(binder.getOrThrow("connectMessage"));
//        restoringSession.privateKey = binder.getBinaryOrThrowgetBinaryOrThrowgetBinaryOrThrow("privateKey");
        restoringSession.setSessionKey(new SymmetricKey(binder.getBinaryOrThrow("sessionKey")));
        restoringSession.setSessionId(binder.getLongOrThrow("sessionId"));
//        restoringSession.nodePublicKey = binder.getBinaryOrThrow("nodePublicKey");
        return restoringSession;
    }

    public String getConnectMessage() {
        synchronized (this) {
            return connectMessage;
        }
    }

    public void setConnectMessage(String connectMessage) {
        synchronized (this) {
            this.connectMessage = connectMessage;
        }
    }

    public PrivateKey getPrivateKey() {
        synchronized (this) {
            return privateKey;
        }
    }

    public void setPrivateKey(PrivateKey privateKey) {
        synchronized (this) {
            this.privateKey = privateKey;
        }
    }

    public SymmetricKey getSessionKey() {
        synchronized (this) {
            return sessionKey;
        }
    }

    public void setSessionKey(SymmetricKey sessionKey) {
        synchronized (this) {
            this.sessionKey = sessionKey;
        }
    }

    public long getSessionId() {
        synchronized (this) {
            return sessionId;
        }
    }

    public void setSessionId(long sessionId) {
        synchronized (this) {
            this.sessionId = sessionId;
        }
    }

    public PublicKey getNodePublicKey() {
        synchronized (this) {
            return nodePublicKey;
        }
    }

    public void setNodePublicKey(PublicKey nodePublicKey) {
        synchronized (this) {
            this.nodePublicKey = nodePublicKey;
        }
    }
}
