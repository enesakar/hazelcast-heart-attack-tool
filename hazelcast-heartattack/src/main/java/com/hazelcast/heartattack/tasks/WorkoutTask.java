package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.heartattack.Coach;
import com.hazelcast.heartattack.Workout;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.logging.Level;

public class WorkoutTask implements Callable, Serializable, HazelcastInstanceAware {
    final static ILogger log = Logger.getLogger(DestroyTrainees.class);

    private transient HazelcastInstance hz;
    private final Workout workout;

    public WorkoutTask(Workout workout) {
        this.workout = workout;
    }

    @Override
    public Object call() throws Exception {
        log.log(Level.INFO, "Workout");
        Coach coach = (Coach) hz.getUserContext().get(Coach.KEY_COACH);
        coach.runWorkout(workout);
        return null;
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hz = hazelcastInstance;
    }
}
