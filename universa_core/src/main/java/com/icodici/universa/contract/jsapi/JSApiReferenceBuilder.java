package com.icodici.universa.contract.jsapi;

import com.icodici.universa.contract.Reference;

public class JSApiReferenceBuilder {

    public JSApiReference createReference(String type) {
        Reference reference = new Reference();
        switch (type) {
            case "TRANSACTIONAL":
                reference.type = Reference.TYPE_TRANSACTIONAL;
                break;
            case "EXISTING_DEFINITION":
                reference.type = Reference.TYPE_EXISTING_DEFINITION;
                break;
            case "EXISTING_STATE":
                reference.type = Reference.TYPE_EXISTING_STATE;
                break;
            default:
                throw new IllegalArgumentException("JSApiReferenceBuilder.createReference error: wrong reference type '"+type+"'");
        }
        return new JSApiReference(reference);
    }

}
