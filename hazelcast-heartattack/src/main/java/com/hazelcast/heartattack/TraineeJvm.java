package com.hazelcast.heartattack;

public class TraineeJvm {
    private final Process process;
    private final String id;

    public TraineeJvm(String id, Process process) {
        this.id = id;
        this.process = process;
    }

    public String getId() {
        return id;
    }

    public Process getProcess() {
        return process;
    }
}
