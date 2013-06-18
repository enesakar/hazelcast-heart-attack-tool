package com.hazelcast.heartattack;

import java.io.Serializable;

public class TraineeSettings implements Serializable{
    private String vmOptions;
    private boolean trackLogging;
    private String hzConfig;
    private int traineeCount;
    private boolean refreshJvm;

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

    public int getTraineeVmCount() {
        return traineeCount;
    }

    public void setTraineeVmCount(int traineeVmCount) {
        this.traineeCount = traineeVmCount;
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
                ", refreshJvm=" + refreshJvm +
                '}';
    }
}
