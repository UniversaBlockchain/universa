package net.sergeych.recordsfile;

import net.sergeych.tools.Binder;
import net.sergeych.utils.Bytes;

import java.util.Arrays;
import java.util.Map;

/**
 * Byte structures packer/unpacker. Uses network byte order (big endian). Allows to pack/unpack integers, byte[] arrasy
 * and string into fixed-size byte sequences. The main idea is to convert integers or longs to/from bytes fields using
 * unsigned notation and network order. Can also store byte arrays and strings.
 */
public class StructureDescriptor {

    private Map<String, FieldDef> fields = new java.util.HashMap<>();
    private int currentOffset = 0;
    private boolean frozen = false;

    /**
     * Full structure size.
     *
     * @return
     */
    public int getSize() {
        return currentOffset;
    }

    /**
     * unpack packed binary data
     *
     * @param res
     *
     * @return
     */
    public Binder unpack(byte[] res) {
        return unpack(res, 0);
    }

    /**
     * Unpack packed binary data staring from a given offset
     *
     * @param data
     * @param fromOffset
     *
     * @return Binder with map of unpacked fields
     */
    public Binder unpack(byte[] data, int fromOffset) {
        Binder result = new Binder();
        fields.forEach((key, fd) -> result.put(key, fd.unpack(data, fromOffset)));
        return result;
    }

    /**
     * Add ingeter field to the structure.
     *
     * @param name
     * @param sizeInBytes in network order, should be between 1 and 8 inclusive
     *
     * @return this
     */
    public StructureDescriptor addField(String name, int sizeInBytes) {
        return addField(name, sizeInBytes, Integer.class);
    }

    /**
     * Add field specifying both size in bytes and class of the object to unpack to.
     *
     * @param name
     * @param sizeInBytes from 1 to 8 inclusive
     * @param itemClass   item unpack to
     *
     * @return this
     */
    public StructureDescriptor addField(String name, int sizeInBytes, Class itemClass) {
        if (fields.containsKey(name))
            throw new IllegalArgumentException("field already defined: " + name);
        if (frozen)
            throw new IllegalStateException("structure us frozen");
        fields.put(name, new FieldDef(currentOffset, sizeInBytes, name, itemClass, 0));
        currentOffset += sizeInBytes;
        return this;
    }

    /**
     * Not yet fully functional
     *
     * @param name
     * @param itemSizeInBytes
     * @param nEntries
     * @param itemClass
     *
     * @return
     */
    private StructureDescriptor addArrayField(String name, int itemSizeInBytes, int nEntries, Class itemClass) {
        if (fields.containsKey(name))
            throw new IllegalArgumentException("field already defined: " + name);
        if (frozen)
            throw new IllegalStateException("structure us frozen");
        FieldDef fd = new FieldDef(currentOffset, itemSizeInBytes, name, itemClass, nEntries);
        fields.put(name, fd);
        currentOffset += fd.getSize();
        return this;
    }

    /**
     * Pack structure into binary data with given values
     *
     * @param data map of values. If any key in the map does not belong to the structure. throws {@link
     *             IllegalArgumentException}
     *
     * @return packed structure
     */
    public byte[] pack(Binder data) {
        return pack(data, false);
    }

    /**
     * Pack structure using given values, optionally ignore keys that are not included in the structure.
     *
     * @param data            map of values to pack
     * @param ignoreNonFields if true ignores any keys not listed in the structure, owtherwise throws {@link
     *                        IllegalArgumentException}.
     *
     * @return packed structure
     */
    public byte[] pack(Binder data, boolean ignoreNonFields) {
        frozen = true;
        byte[] bytes = new byte[currentOffset];
        packTo(bytes, 0, data, ignoreNonFields);
        return bytes;
    }

    /**
     * Pack structure with given data into existing binary array from a specified offset
     *
     * @param bytes           binary array where to put data
     * @param fromOffset      put packed data starting from this offset
     * @param data            map of values to pack
     * @param ignoreNonFields if true ignores any keys not listed in the structure, owtherwise throws {@link
     *                        IllegalArgumentException}.
     */
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

    public StructureDescriptor addByteField(String name) {
        return addField(name, 1);
    }

    public StructureDescriptor addShortField(String name) {
        return addField(name, 2);
    }

    public StructureDescriptor addIntField(String name) {
        return addField(name, 4);
    }

    public StructureDescriptor addLongField(String name) {
        return addField(name, 8, Long.class);
    }

    /**
     * Add byte[] field.
     *
     * @param name
     * @param length in bytes, any positive value.
     *
     * @return this
     */
    public StructureDescriptor addBinaryField(String name, int length) {
        return addArrayField(name, 1, length, Byte.class);
    }

    /**
     * Add string field to be packed usgin system default encoding (utf-8 mainly) into a fixed-size bytes slot. Maximum
     * size in bytes is either (fieldSize - 1) bytes (fieldSize <= 256, or fieldSize - 2 bytes otherwise except for
     * extremely long fields, longer than 64Kb, in which case it be fieldSize - 3. Note that string length in characters
     * could be any depending on the characters used.
     *
     * @param name
     * @param fieldSize
     *
     * @return
     */

    public StructureDescriptor addStringField(String name, int fieldSize) {
        return addArrayField(name, 1, fieldSize, String.class);
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
            if (chars.length + counterSize > size)
                throw new IllegalArgumentException("string too long");
            int start = fromOffset + offset;
            packInt(data, start, chars.length, counterSize);
            System.arraycopy(chars, 0, data, start + counterSize, chars.length);
        }

        private int getCounterSize() {
            if (size <= 256)
                return 1;
            if (size <= 0xFFFF - 2)
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
            packInt(data, offset + fromOffset, acc, size);
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
            int length = (int) readIntegerFrom(data, offset + fromOffset, counterSize);
            int start = offset + fromOffset + counterSize;
            byte[] stringBytes = Arrays.copyOfRange(data, start, start + length);
            return new String(stringBytes);
        }

        private <T> T unpackArray(byte[] data, int fromOffset) {
            if (arrayLength != size)
                throw new RuntimeException("only byte arrays are yet supported");

            int start = offset + fromOffset;
            return (T) Arrays.copyOfRange(data, start, start + arrayLength);
        }

        private long unpackLong(byte[] data, int fromOffset) {
            return readIntegerFrom(data, fromOffset + offset, size);
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
