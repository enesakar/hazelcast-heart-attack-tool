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
        String[] exerciseString = exercise.toString().split("\n");

        StringBuffer sb = new StringBuffer();
        sb.append("HeartAttack[\n");
        sb.append("   message='").append(message).append("'\n");
        sb.append("   coachAddress=").append(coachAddress).append("\n");
        sb.append("   time=").append(time).append("\n");
        sb.append("   traineeAddress=").append(traineeAddress).append("\n");
        sb.append("   traineeId=").append(traineeId).append("\n");
        sb.append("   exercise=").append(exerciseString[0]).append("\n");
        for(int k=1;k<exerciseString.length;k++){
            sb.append("    ").append(exerciseString[k]).append("\n");
        }
        sb.append("]");
        return sb.toString();
    }
}
