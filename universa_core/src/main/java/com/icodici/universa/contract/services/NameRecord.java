package com.icodici.universa.contract.services;

import java.time.ZonedDateTime;
import java.util.Collection;

/**
 * Service storage of a unique name (regulated by the UNS contract) for some amount of time.
 */

public interface NameRecord {
    ZonedDateTime expiresAt();
    String getName();
    String getNameReduced();
    String getDescription();
}
