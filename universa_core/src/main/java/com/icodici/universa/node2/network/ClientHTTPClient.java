/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2.network;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;

import java.io.IOException;

public class ClientHTTPClient extends BasicHTTPClient {

    public ClientHTTPClient(String rootUrlString, PrivateKey clientPrivateKey,
                            PublicKey nodePublicKey) throws IOException {
        super(rootUrlString);
        start(clientPrivateKey, nodePublicKey);
    }
}
