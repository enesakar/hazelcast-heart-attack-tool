package com.hazelcast.heartattack.tasks;

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.logging.Level;

public class EchoTask implements Callable, Serializable {
    final static ILogger log = Logger.getLogger(EchoTask.class.getName());
    private final String msg;

    public EchoTask(String msg) {
        this.msg = msg;
    }

    @Override
    public Object call() throws Exception {
        log.log(Level.INFO, msg);
        return null;
    }

}
