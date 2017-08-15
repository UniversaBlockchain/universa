package net.sergeych.farcall;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * the class to pack Farcall object tp json form and take care of Time() values too. It uses new
 * line (LF, 0x0A, "\n") single character as line separator as it can not be contained in UTF-8
 * encoded JSON strings. That means the JSON should be in compact form, in one line.
 * <p>
 * For data types other than native there is reserved form: { "__type__": "type_name",...}. This
 * implementatoins encodes java Date() instance as
 * <pre>
 *     { "__type__": "datetime", "unixtime", seconds_since_epoch }
 * </pre>
 * using standard unix time - number of seconds since epoch. Note, that it can be either integer or
 * float to get subsecond resolution.
 * <p>
 * This class is thread safe as long as it is used properly, e.g. connected with only one endpoint
 * like the {@link Farcall} instance.
 * <p>
 * Created by sergeych on 12.04.16.
 */
@SuppressWarnings("unused")
public class JsonConnector extends BasicConnector implements Connector {

    public JsonConnector(InputStream in, OutputStream out) {
        super(in, out);
    }

    @Override
    public void send(Map<String, Object> data) throws IOException {
        if (closed.get())
            throw new IOException("connection closed");
        out.write((toJsonString(data) + "\n").getBytes());
    }

    @Override
    public Map<String, Object> receive() throws IOException {
        StringBuilder str = new StringBuilder();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while (true) {
            int code = in.read();
            if (code < 0)
                return null;
            if (code == 0x0A) // "\n"
                break;
            bos.write(code);
        }
        return fromJson(new String(bos.toByteArray()));
    }

}
