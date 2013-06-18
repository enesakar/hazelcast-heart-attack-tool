package com.hazelcast.heartattack;

import java.io.Serializable;

public class TraineeSettings implements Serializable{
    private String traineeVmOptions;
    private boolean traineeTrackLogging;
    private String traineeHzConfig;
    private int traineeVmCount;

    public String getTraineeHzConfig() {
        return traineeHzConfig;
    }

    public void setTraineeHzConfig(String traineeHzConfig) {
        this.traineeHzConfig = traineeHzConfig;
    }

    public boolean isTraineeTrackLogging() {
        return traineeTrackLogging;
    }

    public void setTraineeTrackLogging(boolean traineeTrackLogging) {
        this.traineeTrackLogging = traineeTrackLogging;
    }

    public int getTraineeVmCount() {
        return traineeVmCount;
    }

    public void setTraineeVmCount(int traineeVmCount) {
        this.traineeVmCount = traineeVmCount;
    }

    public String getTraineeVmOptions() {
        return traineeVmOptions;
    }

    public void setTraineeVmOptions(String traineeVmOptions) {
        this.traineeVmOptions = traineeVmOptions;
    }

    @Override
    public String toString() {
        return "CoachSettings{" +
                "traineeHzConfig='" + traineeHzConfig + '\'' +
                ", traineeVmOptions='" + traineeVmOptions + '\'' +
                ", traineeTrackLogging=" + traineeTrackLogging +
                ", traineeVmCount=" + traineeVmCount +
                '}';
    }
}
