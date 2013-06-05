package com.hazelcast.heartattack.exercises;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.heartattack.AbstractExerciseInstance;
import com.hazelcast.heartattack.ExerciseInstance;
import com.hazelcast.heartattack.Exercise;

import java.util.UUID;

public abstract class AbstractExercise implements Exercise {

    private final String clazzName;
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
}
