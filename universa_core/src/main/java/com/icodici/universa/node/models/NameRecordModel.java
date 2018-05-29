package com.icodici.universa.node.models;

import java.time.ZonedDateTime;
import java.util.List;

public class NameRecordModel {
    public long id                            = 0;
    public String name_reduced                = "";
    public String name_full                   = "";
    public String description                 = null;
    public String url                         = null;
    public ZonedDateTime expires_at           = ZonedDateTime.now();
    public long environment_id                = 0;
    public List<NameEntryModel> entries       = null;
}
