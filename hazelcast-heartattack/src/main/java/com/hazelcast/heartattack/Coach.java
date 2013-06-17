package com.hazelcast.heartattack;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.hazelcast.heartattack.Utils.closeQuietly;

public abstract class Coach {
    public static final String KEY_COACH = "Coach";
    public static final String COACH_HEAD_COACH_LOCK = "Coach:headCoachLock";
    public static final String COACH_HEAD_COACH_CONDITION = "Coach:headCoachCondition";
    public static final String COACH_HEAD_COACH_COUNT = "Coach:headCoachCount";

    protected File coachHzFile;
    protected HazelcastInstance coachHz;
    protected HazelcastInstance traineeClient;
    protected IExecutorService traineeExecutor;
    protected final List<Process> traineeProcesses = Collections.synchronizedList(new LinkedList<Process>());

    public List<Process> getTraineeProcesses() {
        return traineeProcesses;
    }

    public void setCoachHzFile(File coachHzFile) {
        this.coachHzFile = coachHzFile;
    }

    public File getCoachHzFile() {
        return coachHzFile;
    }

    public HazelcastInstance getTraineeHazelcastClient() {
        //nasty hack

        if (traineeClient == null) {
            ClientConfig clientConfig = new ClientConfig().addAddress("localhost:6701");
            clientConfig.getGroupConfig()
                    .setName(Trainee.TRAINEE_GROUP)
                    .setPassword("password");
            traineeClient = HazelcastClient.newHazelcastClient(clientConfig);
            traineeExecutor = traineeClient.getExecutorService(Trainee.TRAINEE_EXECUTOR);
        }

        return traineeClient;
    }

    protected HazelcastInstance createCoachHazelcastInstance() {
        FileInputStream in;
        try {
            in = new FileInputStream(coachHzFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        Config config;
        try {
            config = new XmlConfigBuilder(in).build();
        } finally {
            closeQuietly(in);
        }
        config.getUserContext().put(KEY_COACH, this);
        coachHz = Hazelcast.newHazelcastInstance(config);
        return coachHz;
    }
}
