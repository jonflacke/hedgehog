package com.codehedgehog.strawberry;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * Created by Jon on 11/4/2019.
 */
public class EntityTraversalUtility {

    public static boolean isFieldOnParameterizedSubEntity(Class clazz, String fieldName) {
        boolean containsParameterizedType = false;
        String[] fields = fieldName.split("\\.");
        Field clazzField = getFieldOnObject(clazz, fields[0]);
        if (clazzField != null) {
            Class clazzType = clazzField.getType();
            if (clazzField.getGenericType() instanceof ParameterizedType) {
                containsParameterizedType = true;
            } else if (fields.length > 1) {
                containsParameterizedType = isFieldOnParameterizedSubEntity(clazzType, rejoinFieldsFromSecondIndex(fields));
            }
        }
        return containsParameterizedType;
    }

    public static boolean isFieldOnObject(Class clazz, String fieldName) {
        boolean validField = false;
        String[] fields = fieldName.split("\\.");
        Field clazzField = getFieldOnObject(clazz, fields[0]);
        if (clazzField != null) {
            Class clazzType = clazzField.getType();
            if (clazzField.getGenericType() instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) clazzField.getGenericType();
                Type[] typeArguments = parameterizedType.getActualTypeArguments();
                for (Type type : typeArguments) {
                    clazzType = (Class) type;
                }
            }
            if (fields.length > 1) {
                validField = isFieldOnObject(clazzType, rejoinFieldsFromSecondIndex(fields));
            } else {
                validField = true;
            }
        }
        return validField;
    }

    public static  Field getFieldOnObject(Class clazz, String fieldName) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.getName().equals(fieldName)).findFirst().orElse(null);
    }

    public static  Field getDeepestFieldOnObject(Class clazz, String fieldName) {
        String[] fields = fieldName.split("\\.");
        Field clazzField = EntityTraversalUtility.getFieldOnObject(clazz, fields[0]);
        Class clazzType = clazzField.getType();
        if (clazzField.getGenericType() instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) clazzField.getGenericType();
            Type[] typeArguments = parameterizedType.getActualTypeArguments();
            for (Type type : typeArguments) {
                clazzType = (Class) type;
            }
        }
        if (fields.length > 1) {
            return getDeepestFieldOnObject(clazzType, rejoinFieldsFromSecondIndex(fields));
        }
        return clazzField;
    }

    public static  String rejoinFieldsFromSecondIndex(String[] fields) {
        return String.join(".", Arrays.copyOfRange(fields, 1, fields.length));
    }

    public static  String rejoinFieldsWithoutLastIndex(String[] fields) {
        return rejoinFieldsFromSecondIndex(Arrays.copyOfRange(fields, 0, fields.length - 1));
    }
}
