package com.hazelcast.heartattack;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.core.IMap;

public class Trainee {

    public static final String TRAINEE_PARTICIPANT_MAP = "Trainee:ParticipantMap";
    public static final String TRAINEE_EXECUTOR = "Trainee:Executor";
    public static final String TRAINEE_GROUP = "Trainee";

    private final String traineeId;
    private HazelcastInstance hz;
    private IExecutorService executorService;
    private IMap<Object, Object> map;

    public Trainee(String traineeId) {
        this.traineeId = traineeId;
    }

    public void start() {
        Config config = new Config();
        config.getGroupConfig().setName(TRAINEE_GROUP);
        this.hz = Hazelcast.newHazelcastInstance(config);
        this.executorService = hz.getExecutorService(TRAINEE_EXECUTOR);
        this.map = hz.getMap(TRAINEE_PARTICIPANT_MAP);
        this.map.put(traineeId, traineeId);
    }

    public static void main(String[] args) {
        System.out.println("Hazelcast Heart Attack Trainee Started");

        String traineeId = args[0];
        Trainee trainee = new Trainee(traineeId);
        trainee.start();
    }
}
