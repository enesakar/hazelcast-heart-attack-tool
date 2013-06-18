package com.hazelcast.heartattack;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class HeartAttack implements Serializable {
    private final String message;
    private final InetSocketAddress trainerAddress;
    private final String traineeId;

    public HeartAttack(String message, InetSocketAddress trainerAddress, String traineeId) {
        this.message = message;
        this.trainerAddress = trainerAddress;
        this.traineeId = traineeId;
    }

    public String getMessage() {
        return message;
    }

    public String getTraineeId() {
        return traineeId;
    }

    public InetSocketAddress getTrainerAddress() {
        return trainerAddress;
    }

    @Override
    public String toString() {
        return "HeartAttack{" +
                "message='" + message + '\'' +
                ", trainerAddress=" + trainerAddress +
                ", traineeId='" + traineeId + '\'' +
                '}';
    }
}
