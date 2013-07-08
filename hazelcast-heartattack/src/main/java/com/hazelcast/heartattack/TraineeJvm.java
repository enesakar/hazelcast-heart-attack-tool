package com.hazelcast.heartattack;

import com.hazelcast.core.Member;

import java.net.InetSocketAddress;

public class TraineeJvm {
    private final Process process;
    private final String id;
    private volatile Member member;

    public TraineeJvm(String id, Process process) {
        this.id = id;
        this.process = process;
    }


    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    public String getId() {
        return id;
    }

    public Process getProcess() {
        return process;
    }
}
