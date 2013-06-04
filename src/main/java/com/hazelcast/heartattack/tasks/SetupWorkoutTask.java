package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.heartattack.Workout;
import com.hazelcast.heartattack.WorkoutFactory;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SetupWorkoutTask implements Callable, Serializable, HazelcastInstanceAware {
    private final static Logger log = Logger.getLogger(SetupWorkoutTask.class.getName());

    private transient HazelcastInstance hz;
    private final int memberCount;
    private final int memberIndex;
    private final WorkoutFactory factory;

    public SetupWorkoutTask(int memberCount, int memberIndex, WorkoutFactory factory) {
        this.memberCount = memberCount;
        this.memberIndex = memberIndex;
        this.factory = factory;
    }

    @Override
    public Object call() throws Exception {
        try {
            log.info("Setup workout");
            Workout workout = factory.newWorkout(hz, memberIndex, memberCount);
            hz.getUserContext().put("workout", workout);
            workout.setUp();
            return null;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to setup workout", e);
            throw e;
        }
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hz) {
        this.hz = hz;
    }
}
