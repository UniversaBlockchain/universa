package com.icodici.universa.node2.network;

public class VerboseLevel
{
    static public final int NOTHING =           0;
    static public final int BASE =              1;
    static public final int DETAILED =          2;

    public static String intToString(int level) {
        if(level == NOTHING)
            return "nothing";
        if(level == BASE)
            return "base";
        if(level == DETAILED)
            return "detail";
        throw new IllegalArgumentException("Unknown level " + level);
    }

    public static int stringToInt(String level) {
        if(level.equals("nothing"))
            return NOTHING;
        if(level.equals("base"))
            return BASE;
        if(level.equals("detail"))
            return DETAILED;

        throw new IllegalArgumentException("Unknown mode " + level);
    }
}
