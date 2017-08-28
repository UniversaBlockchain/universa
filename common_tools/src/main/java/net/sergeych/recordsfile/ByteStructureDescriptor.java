package net.sergeych.recordsfile;

import net.sergeych.tools.Binder;
import net.sergeych.utils.Bytes;

import java.util.Arrays;
import java.util.Map;

/**
 * Byte structure packer/unpacker. uses network byte order (big endian).
 */
public class ByteStructureDescriptor {

    private Map<String, FieldDef> fields = new java.util.HashMap<>();
    private int currentOffset = 0;
    private boolean frozen = false;

    public int getSize() {
        return currentOffset;
    }

    public Binder unpack(byte[] res) {
        return unpack(res, 0);
    }

    public Binder unpack(byte[] data, int fromOffset) {
        Binder result = new Binder();
        fields.forEach((key, fd) -> result.put(key, fd.unpack(data, fromOffset)));
        return result;
    }

    public ByteStructureDescriptor addField(String name, int sizeInBytes) {
        return addField(name, sizeInBytes, Integer.class);
    }

    public ByteStructureDescriptor addField(String name, int sizeInBytes, Class itemClass) {
        if (fields.containsKey(name))
            throw new IllegalArgumentException("field already defined: " + name);
        if (frozen)
            throw new IllegalStateException("structure us frozen");
        fields.put(name, new FieldDef(currentOffset, sizeInBytes, name, itemClass, 0));
        currentOffset += sizeInBytes;
        return this;
    }

    public ByteStructureDescriptor addArrayField(String name, int itemSizeInBytes, int nEntries, Class itemClass) {
        if (fields.containsKey(name))
            throw new IllegalArgumentException("field already defined: " + name);
        if (frozen)
            throw new IllegalStateException("structure us frozen");
        FieldDef fd = new FieldDef(currentOffset, itemSizeInBytes, name, itemClass, nEntries);
        fields.put(name, fd);
        currentOffset += fd.getSize();
        return this;
    }

    public byte[] pack(Binder data) {
        return pack(data, false);
    }

    public byte[] pack(Binder data, boolean ignoreNonFields) {
        frozen = true;
        byte[] bytes = new byte[currentOffset];
        packTo(bytes, 0, data, ignoreNonFields);
        return bytes;
    }

    public void packTo(byte[] bytes, int fromOffset, Binder data, boolean ignoreNonFields) {
        data.forEach((key, value) -> {
            FieldDef fd = fields.get(key);
            if (fd == null) {
                if (!ignoreNonFields)
                    throw new IllegalArgumentException("undefined field: " + key);
            } else {
                fd.pack(bytes, value, fromOffset);
            }
        });
    }

    public ByteStructureDescriptor addByteField(String name) {
        return addField(name, 1);
    }

    public ByteStructureDescriptor addShortField(String name) {
        return addField(name, 2);
    }

    public ByteStructureDescriptor addIntField(String name) {
        return addField(name, 4);
    }

    public ByteStructureDescriptor addLongField(String name) {
        return addField(name, 8, Long.class);
    }

    public ByteStructureDescriptor addBinaryField(String name, int length) {
        return addArrayField(name, 1, length, Byte.class);
    }

    public ByteStructureDescriptor addStringField(String name, int maxLength) {
        return addArrayField(name, 1, maxLength, String.class);
    }


    private class FieldDef {
        private final int offset;
        private final int size;
        private final String name;
        private Class valueClass;
        private int arrayLength;

        private FieldDef(int offset, int size, String name, Class valueClass, int arrayLength) {
            this.offset = offset;
            this.size = arrayLength > 0 ? size * arrayLength : size;
            this.arrayLength = arrayLength;
            this.name = name;
            this.valueClass = valueClass;
        }

        public void pack(byte[] data, Object value, int fromOffset) {
            if (value instanceof Bytes)
                value = ((Bytes) value).toArray();
            if (value instanceof byte[]) {
                packBinary(data, (byte[]) value, fromOffset);
            } else if (value instanceof String) {
                packString(data, (String) value, fromOffset);
            } else {
                packAsInteger(data, value, fromOffset);
            }
        }

        private void packString(byte[] data, String value, int fromOffset) {
            String s = value;
            if (size != arrayLength)
                throw new IllegalArgumentException("strigns require byte[] binary array to store");
            int counterSize = getCounterSize();
            byte[] chars = s.getBytes();
            if( chars.length + counterSize > size )
                throw new IllegalArgumentException("string too long");
            int start = fromOffset + offset;
            packInt(data, start, chars.length, counterSize);
            System.arraycopy(chars, 0, data, start+counterSize, chars.length);
        }

        private int getCounterSize() {
            if( size <= 256 )
                return 1;
            if( size <= 0xFFFF-2 )
                return 2;
            return 3;
        }

        private void packBinary(byte[] data, byte[] value, int fromOffset) {
            byte[] bytes = value;
            if (size != bytes.length)
                throw new IllegalArgumentException("field size mismatch: required " + size + " given " + bytes.length);
            if (arrayLength != size)
                throw new RuntimeException("binary field array length is wrong");
            System.arraycopy(bytes, 0, data, offset + fromOffset, size);
        }

        private void packAsInteger(byte[] data, Object value, int fromOffset) {
            long acc;
            if (value instanceof Float || value instanceof Double)
                throw new IllegalArgumentException("packing floats is not yet supported");
            if (value instanceof Number)
                acc = ((Number) value).longValue();
            else if (value instanceof Character)
                acc = (int) ((Character) value).charValue();
            else if (value instanceof Boolean)
                acc = (int) value;
            else
                throw new IllegalArgumentException("unsupported numeric type: " + value.getClass().getCanonicalName());
            packInt(data, offset+fromOffset, acc, size);
        }

        private void packInt(byte[] data, int fromOffset, long value, int sizeInBytes) {
            long was = value;
            for (int i = 0; i < sizeInBytes; i++) {
                data[fromOffset + sizeInBytes - i - 1] = (byte) (value & 0xFF);
                value >>= 8;
            }
        }

        public <T> T unpack(byte[] data, int fromOffset) {
            if (arrayLength > 0) {
                return (T) (String.class.isAssignableFrom(valueClass) ? unpackSring(data, fromOffset) : unpackArray(data, fromOffset));
            }
            if (valueClass.isArray()) {
                throw new RuntimeException("value class does not match array flag");
            }
            long val = unpackLong(data, fromOffset);
            if (valueClass == Integer.class) {
                return (T) new Integer((int) (val & 0xFFffFFff));
            }
            if (valueClass == Byte.class) {
                return (T) new Byte((byte) (val & 0xFF));
            }
            if (valueClass == Short.class) {
                return (T) new Short((short) (val & 0xFfFf));
            }
            if (valueClass == Long.class) {
                return (T) new Long(val);
            }
            throw new IllegalArgumentException("can't unpack type: " + valueClass.getCanonicalName());
        }

        private String unpackSring(byte[] data, int fromOffset) {
            int counterSize = getCounterSize();
            int length = (int) readIntegerFrom(data, offset+fromOffset, counterSize);
            int start = offset+fromOffset + counterSize;
            byte [] stringBytes = Arrays.copyOfRange(data, start, start+length);
            return new String(stringBytes);
        }

        private <T> T unpackArray(byte[] data, int fromOffset) {
            if (arrayLength != size)
                throw new RuntimeException("only byte arrays are yet supported");

            int start = offset + fromOffset;
            return (T) Arrays.copyOfRange(data, start, start + arrayLength);
        }

        private long unpackLong(byte[] data, int fromOffset) {
            return readIntegerFrom(data, fromOffset+offset, size);
        }

        private long readIntegerFrom(byte[] data, int fromOffset, int sizeInBytes) {
            long acc = 0;
            for (int i = 0; i < sizeInBytes; i++) {
                acc = (acc << 8) | (data[offset + i] & 0xFF);
            }
            return acc;
        }

        public int getSize() {
            return size;
        }
    }

}
