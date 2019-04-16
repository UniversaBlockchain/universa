package com.icodici.universa.contract.services;

import com.icodici.crypto.KeyAddress;
import com.icodici.universa.HashId;

import java.time.ZonedDateTime;

/**
 * Service for receiving data on the unique name record, regulated by the UNS contract.
 */
public interface NameRecordEntry {

    String getLongAddress();
    String getShortAddress();
    HashId getOrigin();

}
