package com.hazelcast.heartattack.exercises;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.heartattack.Utils;

import java.util.UUID;

public class ExerciseRunner {

    public void run(AbstractExercise exercise, int durationSec) throws Exception {
        HazelcastInstance hz = Hazelcast.newHazelcastInstance();
        exercise.setHazelcastInstance(hz);
        exercise.setExerciseId(UUID.randomUUID().toString());
        exercise.globalSetup();
        exercise.localSetup();
        exercise.start();
        Utils.sleepSeconds(durationSec);
        exercise.stop();
        exercise.globalVerify();
        exercise.localVerify();
        exercise.localTearDown();
        exercise.globalTearDown();
        hz.getLifecycleService().shutdown();
        System.out.println("Finished");
        System.exit(0);
    }
}