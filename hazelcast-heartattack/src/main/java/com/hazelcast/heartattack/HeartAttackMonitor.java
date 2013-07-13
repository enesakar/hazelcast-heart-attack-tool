package com.hazelcast.heartattack;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.Member;

import java.io.File;

public class HeartAttackMonitor implements Runnable {

    private Coach coach;

    public HeartAttackMonitor(Coach coach) {
        this.coach = coach;
    }

    public void run() {
        for (; ; ) {
            for (TraineeVm jvm : coach.getTraineeVmManager().getTraineeJvms()) {
                HeartAttack heartAttack = null;

                if (heartAttack == null) {
                    heartAttack = detectHeartAttackFile(jvm);
                }

                if (heartAttack == null) {
                    heartAttack = detectUnexpectedExit(jvm);
                }

                if (heartAttack == null) {
                    heartAttack = detectMembershipFailure(jvm);
                }

                if (heartAttack != null) {
                    coach.getTraineeVmManager().destroy(jvm);
                    coach.heartAttack(heartAttack);
                }
            }

            Utils.sleepSeconds(1);
        }
    }

    private HeartAttack detectMembershipFailure(TraineeVm jvm) {
        //if the jvm is not assigned a hazelcast address yet.
        if (jvm.getMember() == null) {
            return null;
        }

        Member member = findMember(jvm);
        if (member == null) {
            jvm.getProcess().destroy();
            return new HeartAttack("Hazelcast membership failure (member missing)",
                    coach.getCoachHz().getCluster().getLocalMember().getInetSocketAddress(),
                    jvm.getMember().getInetSocketAddress(),
                    jvm.getId(),
                    coach.getExerciseRecipe());
        }

        return null;
    }

    private Member findMember(TraineeVm jvm) {
        final HazelcastInstance traineeClient = coach.getTraineeVmManager().getTraineeClient();
        if (traineeClient == null) return null;

        for (Member member : traineeClient.getCluster().getMembers()) {
            if (member.getInetSocketAddress().equals(jvm.getMember().getInetSocketAddress())) {
                return member;
            }
        }

        return null;
    }

    private HeartAttack detectHeartAttackFile(TraineeVm jvm) {
        File workoutDir = coach.getWorkoutHome();
        if(workoutDir == null){
            return null;
        }

        File file = new File(workoutDir, jvm.getId() + ".heartattack");
        if (!file.exists()) {
            return null;
        }

        HeartAttack heartAttack = new HeartAttack(
                "out of memory",
                coach.getCoachHz().getCluster().getLocalMember().getInetSocketAddress(),
                jvm.getMember().getInetSocketAddress(),
                jvm.getId(),
                coach.getExerciseRecipe());
        jvm.getProcess().destroy();
        return heartAttack;
    }

    private HeartAttack detectUnexpectedExit(TraineeVm jvm) {
        Process process = jvm.getProcess();
        try {
            if (process.exitValue() != 0) {
                return new HeartAttack(
                        "exit code not 0",
                        coach.getCoachHz().getCluster().getLocalMember().getInetSocketAddress(),
                        jvm.getMember().getInetSocketAddress(),
                        jvm.getId(),
                        coach.getExerciseRecipe());
            }
        } catch (IllegalThreadStateException ignore) {
        }
        return null;
    }
}
