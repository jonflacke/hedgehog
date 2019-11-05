package com.codehedgehog.strawberry;

/**
 * Created by Jon on 11/4/2019.
 */
public class ClassFieldPath {
    private Class clazz;
    private String fieldPath;

    public ClassFieldPath(Class clazz, String fieldPath) {
        this.clazz = clazz;
        this.fieldPath = fieldPath;
    }

    public Class getClazz() {
        return clazz;
    }

    public void setClazz(Class clazz) {
        this.clazz = clazz;
    }

    public String getFieldPath() {
        return fieldPath;
    }

    public void setFieldPath(String fieldPath) {
        this.fieldPath = fieldPath;
    }
}
