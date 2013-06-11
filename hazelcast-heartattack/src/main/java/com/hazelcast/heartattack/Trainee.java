package com.hazelcast.heartattack;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

import java.util.logging.Logger;

public class Trainee {

    private final static Logger log = Logger.getLogger(Trainee.class.getName());

    public static final String TRAINEE_PARTICIPANT_MAP = "Trainee:ParticipantMap";
    public static final String TRAINEE_EXECUTOR = "Trainee:Executor";
    public static final String TRAINEE_GROUP = "Trainee";

    private final String traineeId;
    private HazelcastInstance hz;
    private IMap<Object, Object> map;

    public Trainee(String traineeId) {
        this.traineeId = traineeId;
    }

    public void start() {
        this.hz = createHazelcastInstance();
        this.map = hz.getMap(TRAINEE_PARTICIPANT_MAP);
        this.map.put(traineeId, traineeId);
    }

    public static HazelcastInstance createHazelcastInstance() {
        Config config = new Config();
        config.getGroupConfig().setName(Trainee.TRAINEE_GROUP);
        config.getGroupConfig().setPassword("password");
        config.getNetworkConfig().setPort(6701);
        return Hazelcast.newHazelcastInstance(config);
    }

    public static void main(String[] args) {
        log.info("Starting Hazelcast Heart Attack Trainee");

        String traineeId = args[0];
        Trainee trainee = new Trainee(traineeId);
        trainee.start();

        log.info("Successfully started Hazelcast Heart Attack Trainee");
    }
}
