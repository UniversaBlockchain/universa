package net.sergeych.tools;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import net.sergeych.utils.Ut;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Handy tools to deal with JSON
 * <p>
 * Created by sergeych on 12.04.16.
 */
@SuppressWarnings("ALL")
public class JsonTool {
    /**
     * Deep convert any suitable Java object to corresponding {@link JsonValue} derivative. Converts
     * collections, arrays, maps in depth, converting all containing objects. Note that the map keys
     * are always converted to strings.
     * <p>
     * <ul> <li> All ingeter Java types are converted to integerr value </li> <li>Any privitive
     * array, Object array or Iterable, e.g. any Collection, to the array</li> <li>Any Map converts
     * to the json object ({key: value}, using key.toSting()</li><li>nulls become nulls ;)</li>
     * </ul>
     *
     * @param object
     *         object to convert
     *
     * @return converted object
     *
     * @throws IllegalArgumentException
     *         if there is no known way to convert the object to JSON
     */
    @SuppressWarnings("unchecked")
    public static <T extends JsonValue> T toJson(Object object) {
        if (object == null) {
            return (T) Json.NULL;
        }
        if (object instanceof Number) {
            // Note that these are calls to different methods below!
            if (object instanceof Float || object instanceof Double)
                return (T) Json.value(((Number) object).longValue());
            else
                return (T) Json.value(((Number) object).longValue());
        }
        if (object instanceof String) {
            return (T) Json.value((String) object);
        }
        if (object instanceof Boolean) {
            return (T) Json.value((Boolean) object);
        }
        if (object.getClass().isArray()) {
            JsonArray arr = new JsonArray();
            int length = Array.getLength(object);
            for (int i = 0; i < length; i++) {
                arr.add(toJson(Array.get(object, i)));
            }
            return (T) arr;
        }
        if (object instanceof Iterable) {
            JsonArray arr = new JsonArray();
            for (Object x : (Iterable<?>) object)
                arr.add(toJson(x));
            return (T) arr;
        }
        if (object instanceof Map) {
            Map<?, ?> map = (Map) object;
            JsonObject jsonObject = new JsonObject();
            for (Map.Entry entry : map.entrySet()) {
                jsonObject.set(entry.getKey().toString(), toJson(entry.getValue()));
            }
            return (T) jsonObject;
        }
        if (object instanceof Date) {
            return toJson(Ut.mapFromArray("__type__", "datetime", "unixtime", ((Date) object)
                    .getTime() / 1000.0));
        }
        throw new IllegalArgumentException("Cant convert to json " + object + " of type " +
                                                   object.getClass().getName());
    }

    /**
     * Deep convert to JSON and return it's string representation. See @toJson for details.
     *
     * @param value
     *         object to convert. Anything that could be converted to JSON.
     *
     * @return string value with packed JSON representaton
     */
    static public String toJsonString(Object value) {
        return toJson(value).toString();
    }


    public static <T> T fromJson(JsonValue jsonValue) {
        if( jsonValue.isNumber() ) {
            double real = jsonValue.asDouble();
            return real != (int)real ?(T) Double.valueOf(real) : (T) Long.valueOf((long)real);
        }
        if( jsonValue.isString() )
            return  (T) jsonValue.asString();
        if( jsonValue.isNull() )
            return null;
        if( jsonValue.isTrue() )
            return (T) Boolean.TRUE;
        if( jsonValue.isFalse() )
            return (T) Boolean.FALSE;
        if( jsonValue.isObject() ) {
            JsonObject jo = (JsonObject)jsonValue;
            HashMap<String,Object> result = new HashMap<>();
            for(JsonObject.Member m: jo) {
                result.put(m.getName(), fromJson(m.getValue()));
            }
            return (T)result;
        }
        if( jsonValue.isArray() ) {
            JsonArray array = (JsonArray)jsonValue;
            ArrayList<Object> result = new ArrayList<>(array.size());
            for(JsonValue value: array) {
                result.add(fromJson(value));
            }
            return (T) result;
        }
        throw new IllegalArgumentException("cant convert this type of value: "+jsonValue);
    }

    public static <T> T fromJson(String jsonString) {
        return fromJson(Json.parse(jsonString));
    }
}
