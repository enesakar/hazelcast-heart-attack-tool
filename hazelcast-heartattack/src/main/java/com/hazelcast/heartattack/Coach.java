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
import java.util.List;
import java.util.Vector;

import static com.hazelcast.heartattack.Utils.closeQuietly;

public abstract class Coach {
    protected File coachHzFile;
    protected HazelcastInstance coachHz;
    protected HazelcastInstance traineeHz;
    protected IExecutorService traineeExecutor;
    protected List<Process> traineeProcesses = new Vector();

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

        if (traineeHz == null) {
            ClientConfig clientConfig = new ClientConfig().addAddress("localhost:6701");
            clientConfig.getGroupConfig()
                    .setName(Trainee.TRAINEE_GROUP)
                    .setPassword("password");
            traineeHz = HazelcastClient.newHazelcastClient(clientConfig);
            traineeExecutor = traineeHz.getExecutorService(Trainee.TRAINEE_EXECUTOR);
        }

        return traineeHz;
    }

    protected HazelcastInstance createCoachHazelcastInstance() {
        Config config;

        if (coachHzFile == null) {
            config = new Config();
            config.getGroupConfig().setName("coach");
        } else {
            FileInputStream in;
            try {
                in = new FileInputStream(coachHzFile);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            try {
                config = new XmlConfigBuilder(in).build();
            } finally {
                closeQuietly(in);
            }
        }
        config.getUserContext().put("Coach", this);
        coachHz = Hazelcast.newHazelcastInstance(config);
        return coachHz;
    }
}
