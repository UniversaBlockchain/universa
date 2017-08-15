package net.sergeych.map_serializer;

import java.util.Date;

/**
 * Created by sergeych on 04.01.17.
 */
public class MapSerializerTest {

    static class Rec {
        public int intFfield;
        public String stringField;
        public Date dateField;
        public int[] arrayInt = new int[3];
    }

//    @Test
//    public void toMap() throws Exception {
//        Rec t = new Rec();
//        t.intFfield = 1;
//        t.stringField = "foo";
//        t.dateField = new Date();
//        t.arrayInt[0] = 100;
//        t.arrayInt[1] = 200;
//        final MapSerializer mapSerializer = new MapSerializer();
//        final Map<String, Object> map = mapSerializer.toMap(t, false);
//                Rec r1 = (Rec)mapSerializer.fromMap(map, Rec.class);
//        Boss.trace(map);
//    }

}