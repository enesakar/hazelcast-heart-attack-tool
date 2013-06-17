package com.hazelcast.heartattack;

import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.ICondition;
import com.hazelcast.core.ILock;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import static com.hazelcast.heartattack.Utils.exitWithError;
import static com.hazelcast.heartattack.Utils.getHeartAttackHome;
import static java.lang.String.format;

public class AssistantCoach extends Coach {

    final static ILogger log = Logger.getLogger(AssistantCoach.class.getName());

    private void run() throws InterruptedException, ExecutionException {
        createCoachHazelcastInstance();
        awaitHeadCoachAvailable();
    }

    private void awaitHeadCoachAvailable() {
        log.log(Level.INFO, "Awaiting Head Coach");

        ILock lock = coachHz.getLock(COACH_HEAD_COACH_LOCK);
        lock.lock();
        ICondition condition = lock.newCondition(COACH_HEAD_COACH_CONDITION);
        IAtomicLong available = coachHz.getAtomicLong(COACH_HEAD_COACH_COUNT);

        try {
            while (available.get() == 0) {
                condition.await();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException();
        } finally {
            lock.unlock();
        }

        log.log(Level.INFO, "Head Coach Arrived");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Hazelcast Assistant Coach");
        File heartAttackHome = getHeartAttackHome();
        System.out.printf("HEART_ATTACK_HOME: %s\n",heartAttackHome);

        OptionParser parser = new OptionParser();
        OptionSpec helpSpec = parser.accepts("help", "Show help").forHelp();
        OptionSpec<String> coachHzFileSpec = parser.accepts("coachHzFile", "The Hazelcast xml configuration file for the coach")
                .withRequiredArg().ofType(String.class).defaultsTo(heartAttackHome+File.separator+"conf"+File.separator+"coach-hazelcast.xml");

        OptionSet options;
        try {
            options = parser.parse(args);

            if (options.has(helpSpec)) {
                parser.printHelpOn(System.out);
                System.exit(0);
            }

            AssistantCoach coach = new AssistantCoach();
            File coachHzFile = new File(options.valueOf(coachHzFileSpec));
            if (!coachHzFile.exists()) {
                exitWithError(format("Coach Hazelcast config file [%s] does not exist\n", coachHzFile));
            }
            coach.setCoachHzFile(coachHzFile);

            coach.run();
        } catch (OptionException e) {
            exitWithError(e.getMessage() + "\nUse --help to get overview of the help options.");
        }
    }
}
