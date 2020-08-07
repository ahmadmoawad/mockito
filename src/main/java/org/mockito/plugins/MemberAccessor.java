/*
 * Copyright (c) 2020 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.plugins;

import org.mockito.Incubating;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Incubating
public interface MemberAccessor {

    Object newInstance(Constructor<?> constructor, Object... arguments)
            throws InvocationTargetException;

    Object invoke(Method method, Object target, Object... arguments)
            throws InvocationTargetException;

    Object get(Field field, Object target);

    void set(Field field, Object target, Object value);
}
