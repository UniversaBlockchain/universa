package net.sergeych.map_serializer;

import net.sergeych.tools.Binder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.*;
//import android.annotation.SuppressLint;

/**
 * Serializes and deserializes any class to Map. Intended to use with protocols
 * like JSON or BOSS. Supports aliasing for classes and fields
 * {@link SerialName} and class names in maps.
 *
 * @author sergeych
 */
public class MapSerializer {

    /**
     * Scans all classes accessible from the context class loader which belong
     * to the given package and subpackages.
     *
     * @param packageName
     *         The base package
     *
     * @return The classes
     *
     * @throws ClassNotFoundException
     * @throws IOException
     */
    private static Class<?>[] getClasses(String packageName) throws ClassNotFoundException,
            IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assert classLoader != null;
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<File>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }
        ArrayList<Class<?>> classes = new ArrayList<Class<?>>();
        for (File directory : dirs) {
            classes.addAll(findClasses(directory, packageName));
        }
        return classes.toArray(new Class[classes.size()]);
    }

    /**
     * Recursive method used to find all classes in a given directory and
     * subdirs.
     *
     * @param directory
     *         The base directory
     * @param packageName
     *         The package name for classes found inside the base directory
     *
     * @return The classes
     *
     * @throws ClassNotFoundException
     */
    private static List<Class<?>> findClasses(File directory, String packageName) throws
            ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<Class<?>>();
        if (!directory.exists()) {
            return classes;
        }
        File[] files = directory.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                assert !file.getName().contains(".");
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file
						.getName().length() - 6)));
            }
        }
        return classes;
    }

    /**
     * General error while serializing or deserializing
     *
     * @author sergeych
     */
    public class CantProcessException extends IOException {
        private static final long serialVersionUID = 1L;

        public CantProcessException(Exception inner) {
            super(inner);
        }

        public CantProcessException(String reason) {
            super(reason);
        }
    }

    public HashMap<String, Class<?>> classAliases = new HashMap<String, Class<?>>();

    /**
     * Register all class aliases for a given package. Should be called prior to
     * any operation if class name aliases are used.
     *
     * @param packageName
     *         package name to include all aliased classes from.
     *
     * @throws net.sergeych.map_serializer.MapSerializer.CantProcessException
     */
    public void registerPackage(String packageName) throws CantProcessException {
        try {
            Class<?>[] cc = getClasses(packageName);
            for (Class<?> c : cc) {
                SerialName sn = c.getAnnotation(SerialName.class);
                if (sn != null) {
                    classAliases.put(sn.name(), c);
                }
            }
        } catch (Exception e) {
            logicError(e);
        }
    }

    /**
     * Set the class name alias for the given Class. Note that this works only
     * with classes, not fields.
     *
     * @param cls
     *         class to create alias
     * @param alias
     */
    public void addClassAlias(Class<?> cls, String alias) {
        classAliases.put(alias, cls);
    }

    /**
     * Serialize some object to Map, using aliases if any.
     *
     * @param object
     *         what to serialize
     * @param includeClass
     *         if true, will add "__class" key with object's class name or alias.
     *
     * @return serialized content.
     *
     * @throws net.sergeych.map_serializer.MapSerializer.CantProcessException
     */
    public Binder toMap(Object object, boolean includeClass) throws
            CantProcessException {
        Binder map = new Binder();
        Class<?> cls = object.getClass();
        for (Field f : cls.getDeclaredFields()) {
            int mods = f.getModifiers();
            if (!Modifier.isTransient(mods)) {
                try {
                    f.setAccessible(true);
                    SerialName sn = f.getAnnotation(SerialName.class);
                    map.put(sn == null ? camelToSnakeCase(f.getName()) : sn.name(), f.get(object));
                } catch (Exception e) {
                    logicError(e);
                }
            }
            if (includeClass)
                map.put("__class", className(cls));
        }
        return map;
    }

    /**
     * Could be overriden to support more complicated class-to-name logic.
     * default implementation supports aliases from annotations or explicitly
     * registered aliases or uses canonical name.
     *
     * @param cls
     *         class to find name for
     *
     * @return class name
     */
    protected String className(Class<?> cls) {
        SerialName sn = cls.getAnnotation(SerialName.class);
        if (sn != null) {
            return sn.name();
        }
        return cls.getName();
    }

    /**
     * Could be overriden to support more complicated class-to-name logic.
     * default implementation supports aliases from annotations or explicitly
     * registered aliases or uses canonical name.
     *
     * @param name
     *         class name or alias
     *
     * @return Class for the given name
     *
     * @throws ClassNotFoundException
     */
    protected Class<?> classForName(String name) throws ClassNotFoundException {
        Class<?> cls = classAliases.get(name);
        return cls != null ? cls : Class.forName(name);
    }

    /**
     * Instantiate object by its attributes map. The map MUST contain "__class"
     * key with a valid value.
     *
     * @param map
     *         class name and attributes map
     *
     * @return initialized object
     *
     * @throws net.sergeych.map_serializer.MapSerializer.CantProcessException
     */
    public Object fromMap(Map<String, Object> map) throws CantProcessException {
        HashMap<String, Object> m = new HashMap<String, Object>(map);
        String className = (String) m.remove("__class");
        if (className == null)
            throw new CantProcessException("No class type information to restore");
        return fromMap(m, className);
    }

    /**
     * Instantiate object with a given name and attributes map.
     *
     * @param map
     *         instance attributes
     * @param className
     *         class name or alias
     *
     * @return instantiated object
     *
     * @throws net.sergeych.map_serializer.MapSerializer.CantProcessException
     */
    public Object fromMap(Map<String, Object> map, String className) throws CantProcessException {
        try {
            return fromMap(map, classForName(className));
        } catch (Exception e) {
            logicError(e);
        }
        return null;
    }

    /**
     * Instantiate object of a given Class and initialize it with attributes in
     * a map.
     *
     * @param map
     *         attributes
     * @param cls
     *         Class to instantiate
     *
     * @return T object
     *
     * @throws net.sergeych.map_serializer.MapSerializer.CantProcessException
     */
    public <T> T fromMap(Map<String, Object> map, Class<T> cls) throws CantProcessException {
        try {
            return setInstance(map, cls.newInstance());
        } catch (Exception e) {
            logicError(e);
        }
        return null;
    }

    /**
     * Set object's fields from the map.
     *
     * @param map
     *         map with field values
     * @param object
     *         object which fields will be filled
     *
     * @return object
     *
     * @throws net.sergeych.map_serializer.MapSerializer.CantProcessException
     */
    @SuppressWarnings("unchecked")
    public <T> T setInstance(Map<String, Object> map, T object) throws CantProcessException {
        for (Field f : object.getClass().getDeclaredFields()) {
            int mods = f.getModifiers();
            if (!Modifier.isTransient(mods)) {
                try {
                    f.setAccessible(true);
                    SerialName sn = f.getAnnotation(SerialName.class);
                    Object value = map.get(sn == null ? camelToSnakeCase(f.getName()) : sn.name());
//					if (value instanceof JSONObject.Null)
//						f.set(object, null);
                    if (value != null) {
//						 System.out.println("Cls["+f.getName()+"] = "+f.getGenericType());
                        Type type = f.getGenericType();
                        if (type == String.class) {
                            value = value.toString();
                        } else if (type == Date.class) {
                            if (value instanceof Number) {
                                value = new Date(((Number) value).longValue() * 1000);
                            }
                        } else if (value instanceof Object[] && Collection.class.isAssignableFrom
								(f.getType())) {
                            @SuppressWarnings("rawtypes")
                            Collection coll = (Collection) f.getType().newInstance();
                            for (Object x : ((Object[]) value))
                                coll.add(x);
                            // System.out.println("!!!!!!!!!!" + f.getType()+
                            // " -> "+coll);
                            value = coll;
                        }
                        f.set(object, value);
                    }
                } catch (Exception e) {
                    logicError(e);
                }
            }
        }
        return object;
    }

    protected void logicError(Exception e) throws CantProcessException {
        e.printStackTrace();
        throw new CantProcessException(e);
    }

    /**
     * Convert snake_case to camelCase
     *
     * @param snake
     *         snake_case_string
     *
     * @return camelCaseString
     */
    static public String snakeToCamelCase(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String oneString : snake.split("_")) {
            if (first) {
                sb.append(oneString.toLowerCase(Locale.US));
                first = false;
            } else {
                sb.append(oneString.substring(0, 1).toUpperCase(Locale.US));
                sb.append(oneString.substring(1).toLowerCase(Locale.US));
            }
        }
        return sb.toString();
    }

    /**
     * convert camelCaseString to snake_case_string
     *
     * @param camel
     *         camelString
     *
     * @return snake_string
     */
    static public String camelToSnakeCase(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('_');
                c = Character.toLowerCase(c);
            }
            sb.append(c);
        }
        return sb.toString();
    }

}
