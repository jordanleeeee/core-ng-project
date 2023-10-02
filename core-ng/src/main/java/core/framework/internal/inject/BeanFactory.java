package core.framework.internal.inject;

import core.framework.inject.Inject;
import core.framework.inject.Named;
import core.framework.inject.Value;
import core.framework.internal.module.PropertyManager;
import core.framework.internal.reflect.Fields;
import core.framework.util.Maps;
import core.framework.util.Types;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;

import static core.framework.util.Strings.format;

/**
 * @author neo
 */
public class BeanFactory {
    private final PropertyManager propertyManager;
    private final Map<Key, Object> beans = Maps.newHashMap();

    public BeanFactory(PropertyManager propertyManager) {
        this.propertyManager = propertyManager;
    }

    public void bind(Type type, @Nullable String name, Object instance) {
        if (instance == null) throw new Error("instance must not be null");

        if (!isTypeOf(instance, type))
            throw new Error(format("instance type does not match, type={}, instanceType={}", type.getTypeName(), instance.getClass().getCanonicalName()));

        Object previous = beans.put(new Key(type, name), instance);
        if (previous != null)
            throw new Error(format("found duplicate bean, type={}, name={}, previous={}", type.getTypeName(), name, previous));
    }

    public Object bean(Type type, String name) {
        Object bean = beans.get(new Key(type, name));
        if (bean == null) throw new Error(format("can not find bean, type={}, name={}", type.getTypeName(), name));
        return bean;
    }

    public <T> T create(Class<T> instanceClass) {
        if (instanceClass.isInterface() || Modifier.isAbstract(instanceClass.getModifiers()))
            throw new Error("instance class must be concrete, class=" + instanceClass.getCanonicalName());

        try {
            T instance = construct(instanceClass);
            inject(instance);
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    public <T> void inject(T instance) {
        try {
            Class<?> visitorType = instance.getClass();
            while (!visitorType.equals(Object.class)) {
                for (Field field : visitorType.getDeclaredFields()) {
                    boolean injectBean = field.isAnnotationPresent(Inject.class);
                    boolean injectProperty = field.isAnnotationPresent(Value.class);
                    if (injectBean && injectProperty)
                        throw new Error("cannot inject bean and property at the same time, field=" + Fields.path(field));
                    if (injectBean) {
                        if (Modifier.isStatic(field.getModifiers()))
                            throw new Error("static field must not have @Inject, field=" + Fields.path(field));
                        if (field.trySetAccessible()) {
                            field.set(instance, lookupValue(field));
                        } else {
                            throw new Error("failed to inject field, field=" + Fields.path(field));
                        }
                    } else if (injectProperty) {
                        if (Modifier.isStatic(field.getModifiers()))
                            throw new Error("static field must not have @Property, field=" + Fields.path(field));
                        if (field.trySetAccessible()) {
                            Value value = field.getDeclaredAnnotation(Value.class);
                            Object typedProperty = propertyManager.typedProperty(field, value.value(), value.json(), value.delimiter());
                            field.set(instance, typedProperty);
                        } else {
                            throw new Error("failed to inject property, field=" + Fields.path(field));
                        }
                    }
                }
                visitorType = visitorType.getSuperclass();
            }
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }

    private <T> T construct(Class<T> instanceClass) throws ReflectiveOperationException {
        Constructor<?>[] constructors = instanceClass.getDeclaredConstructors();
        if (constructors.length > 1 || constructors[0].getParameterCount() > 1 || !Modifier.isPublic(constructors[0].getModifiers())) {
            throw new Error(format("instance class must have only one public default constructor, class={}, constructors={}", instanceClass.getCanonicalName(), Arrays.toString(constructors)));
        }
        return instanceClass.getDeclaredConstructor().newInstance();
    }

    private Object lookupValue(Field field) {
        Named named = field.getDeclaredAnnotation(Named.class);
        Type fieldType = stripOwnerType(field.getGenericType());

        String name = named == null ? null : named.value();
        Object bean = beans.get(new Key(fieldType, name));
        if (bean == null) throw new Error(format("can not resolve dependency, type={}, name={}, field={}", fieldType.getTypeName(), name, Fields.path(field)));
        return bean;
    }

    private Type stripOwnerType(Type paramType) {    // type from field/method params could has owner type, which is not used in bind/key
        if (paramType instanceof ParameterizedType parameterizedType)
            return Types.generic((Class<?>) parameterizedType.getRawType(), parameterizedType.getActualTypeArguments());
        return paramType;
    }

    private boolean isTypeOf(Object instance, Type type) {
        if (type instanceof Class) return ((Class<?>) type).isInstance(instance);
        if (type instanceof ParameterizedType) return isTypeOf(instance, ((ParameterizedType) type).getRawType());
        throw new Error("not supported type, type=" + type.getTypeName());
    }
}
