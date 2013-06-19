package com.hazelcast.heartattack;

import java.net.InetSocketAddress;

public class TraineeJvm {
    private final Process process;
    private final String id;
    private volatile  InetSocketAddress address;

    public TraineeJvm(String id, Process process) {
        this.id = id;
        this.process = process;
    }

    void setAddress(InetSocketAddress address) {
        this.address = address;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public String getId() {
        return id;
    }

    public Process getProcess() {
        return process;
    }
}
