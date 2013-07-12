package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.heartattack.HeartAttack;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.logging.Level;

public class EchoHeartAttack implements Callable, Serializable, HazelcastInstanceAware {
    private final static ILogger log = Logger.getLogger(EchoHeartAttack.class);

    private final HeartAttack heartAttack;
    private transient HazelcastInstance hz;

    public EchoHeartAttack(HeartAttack heartAttack) {
        this.heartAttack = heartAttack;
    }

    @Override
    public Object call() throws Exception {
        try {
            if (hz.getCluster().getLocalMember().getInetSocketAddress().equals(heartAttack.getCoachAddress())) {
                log.log(Level.SEVERE, "Local heart attack detected:" + heartAttack);
            } else {
                log.log(Level.SEVERE, "Remote machine heart attack detected:" + heartAttack);
            }
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "failed to echo heart attack", e);
            throw e;
        }
        return null;
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hz) {
        this.hz = hz;
    }
}
