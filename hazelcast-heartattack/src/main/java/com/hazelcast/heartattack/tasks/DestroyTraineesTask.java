package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.heartattack.Coach;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.logging.Level;

import static java.lang.String.format;

public class DestroyTraineesTask implements Callable, Serializable, HazelcastInstanceAware {
    final static ILogger log = Logger.getLogger(DestroyTraineesTask.class.getName());

    private HazelcastInstance hz;

    @Override
    public Object call() throws Exception {
        log.log(Level.INFO, "DestroyTraineesTask");

        long startMs = System.currentTimeMillis();

        Coach coach = (Coach) hz.getUserContext().get(Coach.KEY_COACH);
        coach.destroyTrainees();

        long durationMs = System.currentTimeMillis() - startMs;
        log.log(Level.INFO, format("Destroyed trainees in %s ms", durationMs));
        return null;
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hz = hazelcastInstance;
    }
}
