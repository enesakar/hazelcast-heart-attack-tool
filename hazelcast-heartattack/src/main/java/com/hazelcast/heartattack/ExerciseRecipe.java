package com.hazelcast.heartattack;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ExerciseRecipe implements Serializable {
    private static final long serialVersionUID = 1;
    private final String exerciseId = UUID.randomUUID().toString();

    private Map<String, String> properties = new HashMap();

    public String getClassname() {
        return properties.get("class");
    }

    public String getExerciseId() {
        return exerciseId;
    }

    public String getProperty(String name) {
        return properties.get(name);
    }

    public void setProperty(String name, String value) {
        properties.put(name, value);
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return "ExerciseRecipe{" +
                ", exerciseId='" + exerciseId + '\'' +
                ", properties=" + properties +
                '}';
    }
}
