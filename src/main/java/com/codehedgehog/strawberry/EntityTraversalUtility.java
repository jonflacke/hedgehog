package com.codehedgehog.strawberry;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * Created by Jon on 11/4/2019.
 */
public class EntityTraversalUtility {

    static boolean isFieldOnParameterizedSubEntity(Class clazz, String fieldName) {
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

    static ClassFieldPath getParameterizedEntityAndRemainingFieldPath(ClassFieldPath parentClassFieldPath) {
        ClassFieldPath childClassFieldPath = null;
        String[] fields = parentClassFieldPath.getFieldPath().split("\\.");
        Class testClass = parentClassFieldPath.getClazz();
        for (int i = 0; i < fields.length; i++) {
            Field clazzField = getFieldOnObject(testClass, fields[i]);
            if (clazzField.getGenericType() instanceof ParameterizedType) {
                Class clazzType = clazzField.getType();
                ParameterizedType parameterizedType = (ParameterizedType) clazzField.getGenericType();
                Type[] typeArguments = parameterizedType.getActualTypeArguments();
                for (Type type : typeArguments) {
                    clazzType = (Class) type;
                }
                childClassFieldPath = new ClassFieldPath(clazzType, rejoinFieldsFromIndex(fields, i));
                break;
            } else {
                testClass = clazzField.getType();
            }
        }
        return childClassFieldPath;
    }

    static boolean isFieldOnObject(Class clazz, String fieldName) {
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

    static  Field getFieldOnObject(Class clazz, String fieldName) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.getName().equals(fieldName)).findFirst().orElse(null);
    }

    static  Field getDeepestFieldOnObject(Class clazz, String fieldName) {
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

    static  String rejoinFieldsFromSecondIndex(String[] fields) {
        return rejoinFieldsFromIndex(fields, 1);
    }

    static  String rejoinFieldsWithoutLastIndex(String[] fields) {
        return rejoinFieldsFromSecondIndex(Arrays.copyOfRange(fields, 0, fields.length - 1));
    }

    static String rejoinFieldsFromIndex(String[] fields, Integer startIndex) {
        return String.join(".", Arrays.copyOfRange(fields, startIndex, fields.length));
    }
}
