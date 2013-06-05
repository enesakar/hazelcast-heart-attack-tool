package com.hazelcast.heartattack;

public interface ExerciseInstance {

    //will only be called on a single member in the cluster
    void globalSetup() throws Exception;

    void localSetup() throws Exception;

    void localTearDown() throws Exception;

    //will only be called on a single member in the cluster
    void globalTearDown() throws Exception;

    void start() throws Exception;

    void stop() throws Exception;

    void localVerify() throws Exception;

    void globalVerify() throws Exception;
}
