package com.icodici.universa;

import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.biserializer.BiAdapter;
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
            sb.append(" ["+objectName +"]");
        if( message != null && !message.isEmpty() )
            sb.append(" "+message);
        return sb.toString();
    }

    static {
        DefaultBiMapper.registerAdapter(ErrorRecord.class, new BiAdapter() {
            @Override
            public Binder serialize(Object object, BiSerializer serializer) {
                ErrorRecord er = (ErrorRecord) object;
                return Binder.fromKeysValues(
                        "error", er.error.name(),
                        "object", er.objectName,
                        "message", er.message
                );
            }

            @Override
            public Object deserialize(Binder binder, BiDeserializer deserializer) {
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
