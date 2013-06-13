package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.heartattack.Coach;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;

public class DestroyTraineesTask implements Callable, Serializable, HazelcastInstanceAware {
    final static ILogger log = Logger.getLogger(DestroyTraineesTask.class.getName());

    private HazelcastInstance hz;

    @Override
    public Object call() throws Exception {
        log.log(Level.INFO, "DestroyTraineesTask");

        Coach coach = (Coach) hz.getUserContext().get(SpawnTraineesTask.KEY_COACH);
        List<Process> traineeProcesses = coach.getTraineeProcesses();
        for (Process process : traineeProcesses) {
            process.destroy();
        }

        for (Process process : traineeProcesses) {
            process.waitFor();
        }
        traineeProcesses.clear();

        return null;
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hz = hazelcastInstance;
    }
}
