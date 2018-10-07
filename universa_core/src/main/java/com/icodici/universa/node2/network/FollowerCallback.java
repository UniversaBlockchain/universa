package com.icodici.universa.node2.network;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.services.NImmutableEnvironment;
import com.icodici.universa.contract.services.NNameRecord;
import com.icodici.universa.contract.services.NSmartContract;
import com.icodici.universa.contract.services.SlotContract;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node.StateRecord;
import com.icodici.universa.node.network.BasicHTTPService;
import com.icodici.universa.node2.*;
import com.icodici.universa.node2.network.BasicHttpServer;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.BufferedLogger;
import net.sergeych.utils.Bytes;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FollowerCallback extends BasicHttpServer {

    private final BufferedLogger log;

    FollowerCallback(PrivateKey key, int port, int maxTrheads, BufferedLogger logger) throws IOException {
        super(key, port, maxTrheads, logger);
        log = logger;

    }
}
