package com.hazelcast.heartattack;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Date;

public class HeartAttack implements Serializable {
    private final String message;
    private final InetSocketAddress coachAddress;
    private final InetSocketAddress traineeAddress;
    private final String traineeId;
    private final Date time;
    private final Exercise exercise;

    public HeartAttack(String message, InetSocketAddress coachAddress, InetSocketAddress traineeAddress, String traineeId, Exercise exercise) {
        this.message = message;
        this.coachAddress = coachAddress;
        this.traineeId = traineeId;
        this.time = new Date();
        this.exercise = exercise;
        this.traineeAddress = traineeAddress;
    }

    public String getMessage() {
        return message;
    }

    public String getTraineeId() {
        return traineeId;
    }

    public InetSocketAddress getCoachAddress() {
        return coachAddress;
    }

    public Date getTime() {
        return time;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public InetSocketAddress getTraineeAddress() {
        return traineeAddress;
    }

    @Override
    public String toString() {
        return "HeartAttack{" +
                "  message='" + message + '\'' +
                ", coachAddress=" + coachAddress +
                ", time=" + time +
                ", traineeAddress=" + traineeAddress +
                ", traineeId='" + traineeId + '\'' +
                ", exercise=" + exercise +
                '}';
    }
}
