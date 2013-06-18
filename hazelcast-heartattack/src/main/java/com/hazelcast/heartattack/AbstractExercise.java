package com.hazelcast.heartattack;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hazelcast.core.HazelcastInstance;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.lang.reflect.Field;
import java.util.UUID;

public abstract class AbstractExercise implements Exercise {
    @JsonIgnore
    private final String clazzName;
    @JsonIgnore
    private final String id = UUID.randomUUID().toString();

    public AbstractExercise(Class<? extends AbstractExerciseInstance> clazz) {
        clazzName = clazz.getName();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public ExerciseInstance newInstance(HazelcastInstance hz) {
        try {
            Class<AbstractExerciseInstance> clazz = (Class<AbstractExerciseInstance>) AbstractExercise.class.getClassLoader().loadClass(clazzName);
            AbstractExerciseInstance exercise = clazz.newInstance();
            exercise.setExercise(this);
            exercise.setHazelcastInstance(hz);
            return exercise;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getDescription() {
        return (new ReflectionToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE) {
            protected boolean accept(Field f) {
                String name = f.getName();
                return super.accept(f) && !name.equals("clazzName") && !name.equals("id");
            }
        }).toString();
    }
}
