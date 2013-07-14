package com.hazelcast.heartattack.exercises;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.heartattack.Utils;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.util.UUID;
import java.util.logging.Level;

public class ExerciseRunner {
    private final static ILogger log = Logger.getLogger(ExerciseRunner.class);

    private HazelcastInstance hazelcastInstance;

    public HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
    }

    public void setHazelcastInstance(HazelcastInstance hz) {
        this.hazelcastInstance = hz;
    }

    public void run(Exercise exercise, int durationSec) throws Exception {
        if (hazelcastInstance == null) {
            hazelcastInstance = Hazelcast.newHazelcastInstance();
        }

        exercise.setHazelcastInstance(hazelcastInstance);
        exercise.setExerciseId(UUID.randomUUID().toString());

        log.log(Level.INFO, "starting localSetup");
        exercise.localSetup();
        log.log(Level.INFO, "finished localSetup");

        log.log(Level.INFO, "starting globalSetup");
        exercise.globalSetup();

        log.log(Level.INFO, "starting start");
        exercise.start();
        log.log(Level.INFO, "finished start");

        Utils.sleepSeconds(durationSec);

        log.log(Level.INFO, "starting stop");
        exercise.stop();
        log.log(Level.INFO, "finished stop");

        log.log(Level.INFO, "starting globalVerify");
        exercise.globalVerify();
        log.log(Level.INFO, "finished globalVerify");

        log.log(Level.INFO, "starting localVerify");
        exercise.localVerify();
        log.log(Level.INFO, "finished localVerify");

        log.log(Level.INFO, "starting localTearDown");
        exercise.localTearDown();
        log.log(Level.INFO, "finished localTearDown");

        log.log(Level.INFO, "starting globalTearDown");
        exercise.globalTearDown();
        log.log(Level.INFO, "finished globalTearDown");

        hazelcastInstance.getLifecycleService().shutdown();
        System.out.println("Finished");
    }
}
