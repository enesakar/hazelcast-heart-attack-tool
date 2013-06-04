package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.heartattack.Workout;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;


public class StartTask implements Callable, Serializable, HazelcastInstanceAware {

    private final static Logger log = Logger.getLogger(StartTask.class.getName());

    private transient HazelcastInstance hz;

    @Override
    public Object call() throws Exception {
        try {
            log.info("Start workout");

            Workout workout = (Workout) hz.getUserContext().get("workout");
            if (workout != null) {
                workout.start();
            } else {
                System.out.println("No Workout Found to Start");
            }
            return null;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to start workout", e);
            throw e;
        }
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hz) {
        this.hz = hz;
    }
}
