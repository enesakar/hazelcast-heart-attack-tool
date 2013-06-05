package com.hazelcast.heartattack.tasks;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

public class ShutdownTask implements Callable, Serializable {
    private final static Logger log = Logger.getLogger(ShutdownTask.class.getName());

    @Override
    public Object call() throws Exception {
        log.info("Shutdown");
        System.exit(0);
        return null;
    }
}
