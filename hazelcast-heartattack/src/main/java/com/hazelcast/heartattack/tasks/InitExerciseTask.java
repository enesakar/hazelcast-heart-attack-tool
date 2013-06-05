package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.heartattack.ExerciseInstance;
import com.hazelcast.heartattack.Exercise;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InitExerciseTask implements Callable, Serializable, HazelcastInstanceAware {
    private final static Logger log = Logger.getLogger(InitExerciseTask.class.getName());

    private transient HazelcastInstance hz;
    private final Exercise exercise;

    public InitExerciseTask(Exercise exercise) {
        this.exercise = exercise;
    }

    @Override
    public Object call() throws Exception {
        try {
            log.info("Init Exercise");
            ExerciseInstance exerciseInstance = exercise.newInstance(hz);
            hz.getUserContext().put("exerciseInstance", exerciseInstance);
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

