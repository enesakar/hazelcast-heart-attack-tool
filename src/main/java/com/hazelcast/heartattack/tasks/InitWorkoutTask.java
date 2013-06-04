package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.heartattack.Workout;
import com.hazelcast.heartattack.WorkoutFactory;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InitWorkoutTask implements Callable, Serializable, HazelcastInstanceAware {
    private final static Logger log = Logger.getLogger(InitWorkoutTask.class.getName());

    private transient HazelcastInstance hz;
    private final WorkoutFactory factory;

    public InitWorkoutTask(WorkoutFactory factory) {
        this.factory = factory;
    }

    @Override
    public Object call() throws Exception {
        try {
            log.info("Init workout");
            Workout workout = factory.newWorkout(hz);
            hz.getUserContext().put("workout", workout);
            return null;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to init workout", e);
            throw e;
        }
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hz) {
        this.hz = hz;
    }
}

