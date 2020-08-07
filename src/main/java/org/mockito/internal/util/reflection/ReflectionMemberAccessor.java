/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.util.reflection;

import org.mockito.plugins.MemberAccessor;

import java.lang.reflect.*;
import java.util.Arrays;

public class ReflectionMemberAccessor implements MemberAccessor {

    @Override
    public Object newInstance(Constructor<?> constructor, Object... arguments)
            throws InvocationTargetException {
        silentSetAccessible(constructor, true);
        try {
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to invoke " + constructor + " with " + Arrays.toString(arguments), e);
        } finally {
            silentSetAccessible(constructor, false);
        }
    }

    @Override
    public Object invoke(Method method, Object target, Object... arguments)
            throws InvocationTargetException {
        silentSetAccessible(method, true);
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not invoke " + method + " on " + target, e);
        } finally {
            silentSetAccessible(method, false);
        }
    }

    @Override
    public Object get(Field field, Object target) {
        silentSetAccessible(field, true);
        try {
            return field.get(target);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read " + field + " from " + target);
        } finally {
            silentSetAccessible(field, false);
        }
    }

    @Override
    public void set(Field field, Object target, Object value) {
        silentSetAccessible(field, true);
        try {
            field.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not write " + field + " to " + target, e);
        } finally {
            silentSetAccessible(field, false);
        }
    }

    private static void silentSetAccessible(AccessibleObject object, boolean value) {
        try {
            object.setAccessible(value);
        } catch (Exception ignored) {
        }
    }
}
