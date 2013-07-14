package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.heartattack.Coach;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.logging.Level;

public class CleanupGym implements Callable, Serializable, HazelcastInstanceAware {
    private final static ILogger log = Logger.getLogger(CleanupGym.class);

    private transient HazelcastInstance hz;

    @Override
    public Object call() throws Exception {
        try {
            Coach coach = (Coach) hz.getUserContext().get(Coach.KEY_COACH);
            coach.cleanupGym();
            return null;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to spawn Trainee Virtual Machines", e);
            throw e;
        }
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hz) {
        this.hz = hz;
    }
}

