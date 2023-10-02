package core.framework.internal.module;

import core.framework.internal.json.JSONClassValidator;
import core.framework.internal.reflect.GenericTypes;
import core.framework.internal.validate.Validator;
import core.framework.json.JSON;
import core.framework.util.Strings;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * @author Jordan
 */
class PropertyParser {
    public Object parseTypedProperty(Field field, String key, String propertyValue, boolean json, char delimiter) {
        Class<?> type = field.getType();
        if (delimiter != '\0') {
            return parseCollection(field, key, propertyValue, json, delimiter);
        } else if (json) {
            return parseJsonValue(type, propertyValue);
        } else if (String.class.equals(type)) {
            return propertyValue;
        } else if (Boolean.class.equals(type)) {
            return Boolean.valueOf(propertyValue);
        } else if (Integer.class.equals(type)) {
            return Integer.valueOf(propertyValue);
        } else if (Long.class.equals(type)) {
            return Long.valueOf(propertyValue);
        } else if (Double.class.equals(type)) {
            return Double.valueOf(propertyValue);
        } else if (BigDecimal.class.equals(type)) {
            return new BigDecimal(propertyValue);
        } else if (type.isEnum()) {
            return parseEnumValue(type, propertyValue);
        } else {
            throw new Error(Strings.format("property value {} cannot be injected to field {}", key, type));
        }
    }

    private Object parseCollection(Field field, String key, String propertyValue, boolean json, char delimiter) {
        Class<?> type = field.getType();
        String[] splitProperty = Strings.split(propertyValue, delimiter);
        if (List.class.equals(type)) {
            Class<?> listType = GenericTypes.listValueClass(field.getGenericType());
            return List.of(parseArray(listType, key, splitProperty, json));
        } else if (Set.class.equals(type)) {
            if (json) throw new Error("parse property to set of json is invalid, key=" + key);
            Class<?> setType = GenericTypes.listValueClass(field.getGenericType());
            return Set.of(parseArray(setType, key, splitProperty, false));
        } else if (type.isArray()) {
            Class<?> arrayType = type.componentType();
            return parseArray(arrayType, key, splitProperty, json);
        } else {
            throw new Error(Strings.format("property value {} cannot be injected to collection field {}", key, type));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T[] parseArray(Class<T> arrayType, String key, String[] splitProperty, boolean json) {
        T[] result = (T[]) Array.newInstance(arrayType, splitProperty.length);
        if (json) {
            for (int i = 0; i < splitProperty.length; i++) {
                result[i] = parseJsonValue(arrayType, splitProperty[i]);
            }
        } else if (String.class.equals(arrayType)) {
            result = (T[]) splitProperty;
        } else if (Boolean.class.equals(arrayType)) {
            for (int i = 0; i < splitProperty.length; i++) {
                result[i] = (T) Boolean.valueOf(splitProperty[i]);
            }
        } else if (Integer.class.equals(arrayType)) {
            for (int i = 0; i < splitProperty.length; i++) {
                result[i] = (T) Integer.valueOf(splitProperty[i]);
            }
        } else if (Long.class.equals(arrayType)) {
            for (int i = 0; i < splitProperty.length; i++) {
                result[i] = (T) Long.valueOf(splitProperty[i]);
            }
        } else if (Double.class.equals(arrayType)) {
            for (int i = 0; i < splitProperty.length; i++) {
                result[i] = (T) Double.valueOf(splitProperty[i]);
            }
        } else if (BigDecimal.class.equals(arrayType)) {
            for (int i = 0; i < splitProperty.length; i++) {
                result[i] = (T) new BigDecimal(splitProperty[i]);
            }
        } else if (arrayType.isEnum()) {
            for (int i = 0; i < splitProperty.length; i++) {
                result[i] = (T) parseEnumValue(arrayType, splitProperty[i]);
            }
        } else {
            throw new Error(Strings.format("property value {} cannot be injected to collection field {}", key, arrayType));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T extends Enum<?>> T parseEnumValue(Class<?> enumClass, String value) {
        return JSON.fromEnumValue((Class<T>) enumClass, value);
    }

    private <T> T parseJsonValue(Class<T> type, String value) {
        new JSONClassValidator(type).validate();
        T json = JSON.fromJSON(type, value);
        Validator<T> validator = Validator.of(type);
        validator.validate(json, false);
        return json;
    }
}
