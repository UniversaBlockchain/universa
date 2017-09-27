/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.biserializer;

import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@BiType(name="foobar1")
class Test1 implements BiSerializable {

    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    private Test1() {}

    public Test1(String value) {
        this.value = value;
    }

    @Override
    public void deserialize(Binder data,BiDeserializer deserializer) {
        value = data.getStringOrThrow("value");
    }

    @Override
    public Binder serialize(BiSerializer bis) {
        return Binder.of("value", value);
    }
}

class Test2 extends Test1 {

    public Test2() {
        super("crap");
        bar = "invalid";
    }

    public String getBar() {
        return bar;
    }

    private String bar;

    public Test2(String barval) {
        super("bad");
        bar = barval;
    }

    @Override
    public Binder serialize(BiSerializer s) {
        Binder b = super.serialize(s);
        b.put("bar", bar);
        return b;
    }

    @Override
    public void deserialize(Binder data,BiDeserializer deserializer) {
        super.deserialize(data, deserializer);
        bar = data.getStringOrThrow("bar");
    }
}

class Rebinder extends Binder implements BiSerializable {

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        clear();
        putAll(data);
    }

    @Override
    public Binder serialize(BiSerializer s) {
        return this;
    }
}

public class BiMapperTest {
    @Test
    public void serialize() throws Exception {
        // Check proper serialization of the structure/single object
        ZonedDateTime now = ZonedDateTime.now();
        Binder res = DefaultBiMapper.serialize(
                Binder.of(
                        "time", now,
                        "hello", "world"
                )
        );
        assertEquals("world", res.get("hello"));
        assertEquals("unixtime", res.getStringOrThrow("time", "__type"));

        Binder restored = DefaultBiMapper.deserialize(res);
        assertEquals(now.truncatedTo(ChronoUnit.SECONDS), restored.get("time"));
        assertEquals(now.truncatedTo(ChronoUnit.SECONDS), DefaultBiMapper.deserialize(DefaultBiMapper.serialize(now)));
    }

    @Test
    public void autoSerializable() throws Exception {
        // TODO: autoserialize class hierarchy
        Test1 t1 = new Test1("foo");
        DefaultBiMapper.registerClass(Test1.class);
        Binder s = DefaultBiMapper.serialize(t1);
        assertEquals(s.getStringOrThrow("__type"), "foobar1");
        assertEquals(s.getStringOrThrow("value"), "foo");
        Test1 t2 = DefaultBiMapper.deserialize(s);
        assertThat(t2, instanceOf(Test1.class) );
        assertEquals("foo", t2.getValue());
    }

    @Test
    public void autoSerializableInheritance() throws Exception {
        // TODO: autoserialize class hierarchy
        Test1 t1 = new Test2("foo");
        DefaultBiMapper.registerClass(Test2.class);
        Binder s = DefaultBiMapper.serialize(t1);
        assertEquals(s.getStringOrThrow("__type"), "net.sergeych.biserializer.Test2");
        Test2 t2 = DefaultBiMapper.deserialize(s);
        assertThat(t2, instanceOf(Test1.class) );
        assertEquals("bad", t2.getValue());
        assertEquals("foo", t2.getBar());
    }

    @Test
    public void autoSerializableBinder() throws Exception {
        // if the object is instance of Map (for example Binder), it could also have
        // it's own serialization:
        Rebinder b = new Rebinder();
        DefaultBiMapper.registerClass(Rebinder.class);
        b.put("foo", "bar");
        Binder s = DefaultBiMapper.serialize(b);
        Object x = DefaultBiMapper.deserialize(s);
        assertThat(x, instanceOf(Rebinder.class));
        assertEquals("bar", ((Map)x).get("foo"));
    }

    @Test
    public void processBytes() throws Exception {
        byte x[] = Do.randomBytes(10);
        Binder s = DefaultBiMapper.serialize(x);
        byte[] result = DefaultBiMapper.deserialize(s);
        assertArrayEquals(x, result);
    }
}