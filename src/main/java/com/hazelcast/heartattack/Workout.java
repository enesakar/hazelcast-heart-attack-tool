package com.hazelcast.heartattack;

public interface Workout {

    void setUp()throws Exception;

    void tearDown()throws Exception;

    void start()throws Exception;

    void stop()throws Exception;

    void verifyNoHeartAttack()throws Exception;
}
