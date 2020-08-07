/*
 * Copyright (c) 2020 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.util.reflection;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodCall;
import org.mockito.exceptions.base.MockitoInitializationException;
import org.mockito.plugins.MemberAccessor;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.mockito.internal.util.StringUtil.join;

public class InstrumentationMemberAccessor implements MemberAccessor {

    private static final Instrumentation INSTRUMENTATION;
    private static final Dispatcher DISPATCHER;

    private static final Throwable INITIALIZATION_ERROR;

    static {
        Instrumentation instrumentation;
        Dispatcher dispatcher;
        Throwable throwable;
        try {
            instrumentation = ByteBuddyAgent.install();
            dispatcher =
                    new ByteBuddy()
                            .subclass(Dispatcher.class)
                            .method(named("getLookup"))
                            .intercept(MethodCall.invoke(MethodHandles.class.getMethod("lookup")))
                            .method(named("getModule"))
                            .intercept(
                                    MethodCall.invoke(Class.class.getMethod("getModule"))
                                            .onMethodCall(
                                                    MethodCall.invoke(
                                                            Object.class.getMethod("getClass"))))
                            .method(named("setAccessible"))
                            .intercept(
                                    MethodCall.invoke(
                                                    AccessibleObject.class.getMethod(
                                                            "setAccessible", boolean.class))
                                            .onArgument(0)
                                            .withArgument(1))
                            .make()
                            .load(
                                    InstrumentationMemberAccessor.class.getClassLoader(),
                                    ClassLoadingStrategy.Default.WRAPPER)
                            .getLoaded()
                            .getConstructor()
                            .newInstance();
            throwable = null;
        } catch (Throwable t) {
            instrumentation = null;
            dispatcher = null;
            throwable = t;
        }
        INSTRUMENTATION = instrumentation;
        DISPATCHER = dispatcher;
        INITIALIZATION_ERROR = throwable;
    }

    private final MethodHandle getModule, isOpen, redefineModule;

    public InstrumentationMemberAccessor() {
        if (INITIALIZATION_ERROR != null) {
            throw new MockitoInitializationException(
                    join(
                            "Could not initialize the Mockito instrumentation member accessor",
                            "",
                            "This is unexpected on JVMs from Java 9 or later - possibly, the instrumentation API could not be resolved"),
                    INITIALIZATION_ERROR);
        }
        try {
            Class<?> module = Class.forName("java.lang.Module");
            getModule =
                    MethodHandles.publicLookup()
                            .findVirtual(Class.class, "getModule", MethodType.methodType(module));
            isOpen =
                    MethodHandles.publicLookup()
                            .findVirtual(
                                    module,
                                    "isOpen",
                                    MethodType.methodType(boolean.class, String.class, module));
            redefineModule =
                    MethodHandles.publicLookup()
                            .findVirtual(
                                    Instrumentation.class,
                                    "redefineModule",
                                    MethodType.methodType(
                                            void.class,
                                            module,
                                            Set.class,
                                            Map.class,
                                            Map.class,
                                            Set.class,
                                            Map.class));
        } catch (Throwable t) {
            throw new MockitoInitializationException(
                    "Could not resolve instrumentation invoker", t);
        }
    }

    @Override
    public Object newInstance(Constructor<?> constructor, Object... arguments)
            throws InvocationTargetException {
        try {
            Object module = getModule.bindTo(constructor.getDeclaringClass()).invokeWithArguments();
            String packageName = constructor.getDeclaringClass().getPackage().getName();
            assureOpen(module, packageName);
            DISPATCHER.setAccessible(constructor, true);
            try {
                MethodHandle handle = DISPATCHER.getLookup().unreflectConstructor(constructor);
                try {
                    return handle.invokeWithArguments(arguments);
                } catch (Throwable t) {
                    throw new InvocationTargetException(t);
                }
            } finally {
                DISPATCHER.setAccessible(constructor, false);
            }
        } catch (InvocationTargetException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(
                    "Could not construct "
                            + constructor
                            + " with arguments "
                            + Arrays.toString(arguments),
                    t);
        }
    }

    @Override
    public Object invoke(Method method, Object target, Object... arguments)
            throws InvocationTargetException {
        try {
            Object module = getModule.bindTo(method.getDeclaringClass()).invokeWithArguments();
            String packageName = method.getDeclaringClass().getPackage().getName();
            assureOpen(module, packageName);
            DISPATCHER.setAccessible(method, true);
            try {
                MethodHandle handle = DISPATCHER.getLookup().unreflect(method);
                if (!Modifier.isStatic(method.getModifiers())) {
                    handle = handle.bindTo(target);
                }
                try {
                    return handle.invokeWithArguments(arguments);
                } catch (Throwable t) {
                    throw new InvocationTargetException(t);
                }
            } finally {
                DISPATCHER.setAccessible(method, false);
            }
        } catch (InvocationTargetException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(
                    "Could not invoke "
                            + method
                            + " on "
                            + target
                            + " with arguments "
                            + Arrays.toString(arguments),
                    t);
        }
    }

    @Override
    public Object get(Field field, Object target) {
        try {
            Object module = getModule.bindTo(field.getDeclaringClass()).invokeWithArguments();
            String packageName = field.getDeclaringClass().getPackage().getName();
            assureOpen(module, packageName);
            DISPATCHER.setAccessible(field, true);
            try {
                MethodHandle handle = DISPATCHER.getLookup().unreflectGetter(field);
                if (!Modifier.isStatic(field.getModifiers())) {
                    handle = handle.bindTo(target);
                }
                return handle.invokeWithArguments();
            } finally {
                DISPATCHER.setAccessible(field, false);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("Could not read " + field + " on " + target, t);
        }
    }

    @Override
    public void set(Field field, Object target, Object value) {
        try {
            Object module = getModule.bindTo(field.getDeclaringClass()).invokeWithArguments();
            String packageName = field.getDeclaringClass().getPackage().getName();
            assureOpen(module, packageName);
            DISPATCHER.setAccessible(field, true);
            try {
                MethodHandle handle = DISPATCHER.getLookup().unreflectSetter(field);
                if (!Modifier.isStatic(field.getModifiers())) {
                    handle = handle.bindTo(target);
                }
                handle.invokeWithArguments(value);
            } finally {
                DISPATCHER.setAccessible(field, false);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("Could not read " + field + " on " + target, t);
        }
    }

    private void assureOpen(Object module, String packageName) throws Throwable {
        if (!(Boolean) isOpen.invokeWithArguments(module, packageName, DISPATCHER.getModule())) {
            redefineModule
                    .bindTo(INSTRUMENTATION)
                    .invokeWithArguments(
                            module,
                            Collections.emptySet(),
                            Collections.emptyMap(),
                            Collections.singletonMap(
                                    packageName, Collections.singleton(DISPATCHER.getModule())),
                            Collections.emptySet(),
                            Collections.emptyMap());
        }
    }

    public interface Dispatcher {

        MethodHandles.Lookup getLookup();

        Object getModule();

        void setAccessible(AccessibleObject object, boolean accessible);
    }
}
