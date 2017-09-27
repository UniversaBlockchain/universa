/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.biserializer;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation that allows specify alias name for class and/or for field, see {@link BiSerializable}. Note that if you use
 * it with classes, you should (1) implement {@link BiSerializable} and provide nonparametric constructor (posiible provzate one)
 * and reister your class wither with {@link DefaultBiMapper}, {@link BossBiMapper} or custom instance of {@link BiMapper}
 * prior to de/serialize.
 *
 * See {@link BiSerializable} for more.
 *
 * @author sergeych
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface BiType {
    String name();
}
