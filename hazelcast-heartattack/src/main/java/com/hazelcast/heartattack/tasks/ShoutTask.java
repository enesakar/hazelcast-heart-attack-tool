package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.heartattack.Coach;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.logging.Level;

public class ShoutTask implements Callable, Serializable, HazelcastInstanceAware {
    private final static ILogger log = Logger.getLogger(ShoutTask.class);

    private final Callable task;
    private transient HazelcastInstance hz;

    public ShoutTask(Callable task) {
        this.task = task;
    }

    @Override
    public Object call() throws Exception {
        log.log(Level.INFO, "ShoutTask");

        Coach coach = (Coach) hz.getUserContext().get(Coach.KEY_COACH);
        coach.shoutToTrainees(task);

        return null;
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hz = hazelcastInstance;
    }
}

