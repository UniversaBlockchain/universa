/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.biserializer;

import net.sergeych.map_serializer.MapSerializer;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation that allows specify alias name for class and/or
 * for field, see {@link MapSerializer}. Note that if you use
 * it with classes, you should invoke {@link MapSerializer#registerPackage(String)}
 * prior to deserialization.
 * 
 * @author sergeych
 *
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface BiType {
	String name();
}
