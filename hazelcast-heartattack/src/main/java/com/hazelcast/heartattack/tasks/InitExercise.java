package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.heartattack.Exercise;
import com.hazelcast.heartattack.ExerciseInstance;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.logging.Level;

public class InitExercise implements Callable, Serializable, HazelcastInstanceAware {
    final static ILogger log = Logger.getLogger(InitExercise.class.getName());

    private transient HazelcastInstance hz;
    private final Exercise exercise;

    public InitExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    @Override
    public Object call() throws Exception {
        try {
            log.log(Level.INFO, "Init Exercise");
            ExerciseInstance exerciseInstance = exercise.newInstance(hz);
            hz.getUserContext().put(ExerciseInstance.EXERCISE_INSTANCE, exerciseInstance);
            return null;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to init Exercise", e);
            throw e;
        }
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hz) {
        this.hz = hz;
    }
}

