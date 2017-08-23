package com.icodici.universa;

import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;

public class ErrorRecord {
    private String objectName;
    private String message;
    private Errors error;

    public ErrorRecord(Errors error, String objectName, String message) {
        this.objectName = objectName;
        this.message = message;
        this.error = error;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getMessage() {
        return message;
    }

    public Errors getError() {
        return error;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(error.name());
        if( objectName != null && !objectName.isEmpty())
            sb.append("@"+objectName);
        if( message != null && !message.isEmpty() )
            sb.append(" "+message);
        return sb.toString();
    }

    static {
        Boss.registerAdapter(ErrorRecord.class, new Boss.Adapter() {
            @Override
            public Binder serialize(Object object) {
                ErrorRecord er = (ErrorRecord) object;
                return Binder.fromKeysValues(
                        "error", er.error.name(),
                        "object", er.objectName,
                        "message", er.message
                );
            }

            @Override
            public Object deserialize(Binder binder) {
                return new ErrorRecord(
                        Errors.valueOf(binder.getStringOrThrow("error")),
                        binder.getStringOrThrow("object"),
                        binder.getString("message", "")
                );
            }

            @Override
            public String typeName() {
                return "error";
            }
        });
    }
}
