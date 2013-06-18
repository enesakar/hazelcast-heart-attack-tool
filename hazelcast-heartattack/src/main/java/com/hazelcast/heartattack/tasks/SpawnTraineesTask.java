package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.heartattack.Coach;
import com.hazelcast.heartattack.TraineeSettings;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.logging.Level;

import static java.lang.String.format;

public class SpawnTraineesTask implements Callable, Serializable, HazelcastInstanceAware {
    final static ILogger log = Logger.getLogger(InitExerciseTask.class.getName());

    private transient HazelcastInstance hz;
    private final TraineeSettings settings;

    public SpawnTraineesTask(TraineeSettings settings) {
        this.settings = settings;
    }

    @Override
    public Object call() throws Exception {
        log.log(Level.INFO, format("Spawning %s trainees", settings.getTraineeVmCount()));
        long startMs = System.currentTimeMillis();

        try {
            Coach coach = (Coach) hz.getUserContext().get(Coach.KEY_COACH);
            coach.spawnTrainees(settings);
            long durationMs = System.currentTimeMillis() - startMs;
            log.log(Level.INFO, format("Spawned %s trainees in %s ms", settings.getTraineeVmCount(), durationMs));
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
