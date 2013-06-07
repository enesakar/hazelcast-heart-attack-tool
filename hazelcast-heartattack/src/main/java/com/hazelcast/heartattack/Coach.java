package com.hazelcast.heartattack;

import com.hazelcast.core.HazelcastInstance;

public interface Coach {
    HazelcastInstance getTraineeHazelcastInstance();
}
