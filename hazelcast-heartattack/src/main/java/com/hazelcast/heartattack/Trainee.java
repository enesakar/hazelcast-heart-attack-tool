package com.hazelcast.heartattack;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.FileNotFoundException;
import java.util.logging.Level;

public class Trainee {

    final static ILogger log = Logger.getLogger(Trainee.class.getName());

    public static final String TRAINEE_PARTICIPANT_MAP = "Trainee:ParticipantMap";
    public static final String TRAINEE_EXECUTOR = "Trainee:Executor";

    private String traineeId;
    private HazelcastInstance hz;
    private IMap<Object, Object> map;
    private String traineeHzFile;

    public void setTraineeId(String traineeId) {
        this.traineeId = traineeId;
    }

    private void setTraineeHzFile(String traineeHzFile) {
        this.traineeHzFile = traineeHzFile;
    }

    public void start() {
        this.hz = createHazelcastInstance();
        this.map = hz.getMap(TRAINEE_PARTICIPANT_MAP);
        this.map.put(traineeId, traineeId);
    }

    public HazelcastInstance createHazelcastInstance() {
        XmlConfigBuilder configBuilder;
        try {
            configBuilder = new XmlConfigBuilder(traineeHzFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        Config config = configBuilder.build();
        return Hazelcast.newHazelcastInstance(config);
    }

    public static void main(String[] args) {
        log.log(Level.INFO, "Starting Hazelcast Heart Attack Trainee");

        String traineeId = args[0];
        log.log(Level.INFO, "Trainee id:" + traineeId);
        String traineeHzFile = args[1];
        log.log(Level.INFO, "Trainee hz config file:" + traineeHzFile);

        Trainee trainee = new Trainee();
        trainee.setTraineeId(traineeId);
        trainee.setTraineeHzFile(traineeHzFile);
        trainee.start();

        log.log(Level.INFO, "Successfully started Hazelcast Heart Attack Trainee");
    }

}
