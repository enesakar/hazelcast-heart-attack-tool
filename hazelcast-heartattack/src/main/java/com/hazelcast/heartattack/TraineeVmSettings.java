package com.hazelcast.heartattack;

import java.io.Serializable;

public class TraineeVmSettings implements Serializable{
    private String vmOptions;
    private boolean trackLogging;
    private String hzConfig;
    private int traineeCount;
    private int traineeStartupTimeout;
    private boolean refreshJvm;

    public int getTraineeStartupTimeout() {
        return traineeStartupTimeout;
    }

    public void setTraineeStartupTimeout(int traineeStartupTimeout) {
        this.traineeStartupTimeout = traineeStartupTimeout;
    }

    public boolean isRefreshJvm() {
        return refreshJvm;
    }

    public void setRefreshJvm(boolean refreshJvm) {
        this.refreshJvm = refreshJvm;
    }

    public String getHzConfig() {
        return hzConfig;
    }

    public void setHzConfig(String hzConfig) {
        this.hzConfig = hzConfig;
    }

    public boolean isTrackLogging() {
        return trackLogging;
    }

    public void setTrackLogging(boolean trackLogging) {
        this.trackLogging = trackLogging;
    }

    public int getTraineeCount() {
        return traineeCount;
    }

    public void setTraineeCount(int traineeCount) {
        this.traineeCount = traineeCount;
    }

    public String getVmOptions() {
        return vmOptions;
    }

    public void setVmOptions(String vmOptions) {
        this.vmOptions = vmOptions;
    }

    @Override
    public String toString() {
        return "TraineeSettings{" +
                "hzConfig='" + hzConfig + '\'' +
                ", vmOptions='" + vmOptions + '\'' +
                ", trackLogging=" + trackLogging +
                ", traineeVmCount=" + traineeCount +
                ", traineeStartupTimeout=" + traineeStartupTimeout +
                ", refreshJvm=" + refreshJvm +
                '}';
    }
}
