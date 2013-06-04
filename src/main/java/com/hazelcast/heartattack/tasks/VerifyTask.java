package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.heartattack.Workout;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

public class VerifyTask implements Callable, Serializable, HazelcastInstanceAware {
    private final static Logger log = Logger.getLogger(VerifyTask.class.getName());

    private transient HazelcastInstance hz;

    @Override
    public Object call() throws Exception {
        log.info("Verify Workout");

        Workout workout = (Workout) hz.getUserContext().get("workout");
        if (workout != null) {
            workout.verifyNoHeartAttack();
        }
        return null;
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hz) {
        this.hz = hz;
    }
}
