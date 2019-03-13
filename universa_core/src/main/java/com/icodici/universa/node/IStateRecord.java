package com.icodici.universa.node;

import java.time.ZonedDateTime;

public interface IStateRecord {
    ItemState getState();
    ZonedDateTime getExpiresAt();
    ZonedDateTime getCreatedAt();
}
