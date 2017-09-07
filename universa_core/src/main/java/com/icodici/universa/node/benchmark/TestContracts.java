package com.icodici.universa.node.benchmark;


import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Role;
import com.icodici.universa.contract.RoleLink;
import com.icodici.universa.contract.permissions.ChangeOwnerPermission;
import net.sergeych.tools.AsyncEvent;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Generate set of test contracts
 */
public class TestContracts implements AutoCloseable {
    private final PrivateKey key;
    private final PublicKey publicKey;
    private ConcurrentLinkedQueue<Contract> contracts = new ConcurrentLinkedQueue<>();
    private ThreadPoolExecutor pool;

    public TestContracts(int nThreads) {
        pool = new ThreadPoolExecutor(nThreads, nThreads, 500, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        key = new PrivateKey(2048);
        publicKey = key.getPublicKey();
    }

    public Collection<Contract> generate(int nContracts) throws InterruptedException {
        clear();
        AsyncEvent done = new AsyncEvent();
        for( int i=0; i<nContracts; i++ ) {
            pool.submit(()->{
                Contract c = new Contract();
                // issuer role is a key for a new contract
                Role r = c.setIssuerKeys(publicKey);
                // issuer is owner, link roles
                c.registerRole(new RoleLink("owner", "issuer"));
                // owner can change permission
                c.addPermission(new ChangeOwnerPermission(r));
                // issuer should sign
                c.addSignerKey(key);
                // done, let's seal and check
                c.seal();
                if( !c.isOk() )
                    throw new RuntimeException("Failed to create contract: "+c.getErrorsString());
                contracts.add(c);
                if( contracts.size() == nContracts)
                    done.fire(null);
                return null;
            });
        }
        done.await();
        return contracts;
    }

    public Collection<Contract> getContracts() {
        return contracts;
    }

    public synchronized void close() {
        if( pool != null ) {
            pool.shutdown();
            pool = null;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    public void clear() {
        contracts.clear();
    }


    public PublicKey getPublicKey() {
        return publicKey;
    }
}
