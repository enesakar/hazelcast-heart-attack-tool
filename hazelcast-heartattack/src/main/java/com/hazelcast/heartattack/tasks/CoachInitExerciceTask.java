package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.heartattack.Coach;
import com.hazelcast.heartattack.Exercise;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.logging.Level;

public class CoachInitExerciceTask implements Callable, Serializable, HazelcastInstanceAware {
    final static ILogger log = Logger.getLogger(InitExerciseTask.class.getName());

    private transient HazelcastInstance hz;
    private final Exercise exercise;

    public CoachInitExerciceTask(Exercise exercise) {
        this.exercise = exercise;
    }

    @Override
    public Object call() throws Exception {
        try {
            Coach coach = (Coach) hz.getUserContext().get(Coach.KEY_COACH);
            coach.setExercise(exercise);
            return null;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to init coach Exercise", e);
            throw e;
        }
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hz) {
        this.hz = hz;
    }
}
