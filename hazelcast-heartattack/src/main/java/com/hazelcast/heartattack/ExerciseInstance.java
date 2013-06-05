package com.hazelcast.heartattack;


/**
 * The ExerciseInstance is the 'thing' that contains the actual logic we want to run. See {@link Exercise} for more
 * information.
 * <p/>
 * Order of lifecycle methods:
 * <ol>
 * <li>{@link #globalSetup()}</li>
 * <li>{@link #localSetup()}</li>
 * <li>{@link #start()}</li>
 * <li>{@link #stop()}</li>
 * <li>{@link #localVerify()}</li>
 * <li>{@link #globalVerify()}</li>
 * <li>{@link #globalTearDown()}</li>
 * <li>{@link #localTearDown()}</li>
 * </ol>
 */
public interface ExerciseInstance {

    /**
     * Sets up this ExerciseInstance
     * <p/>
     * This method will only be called on a single members of the cluster.
     *
     * @throws Exception
     */
    void globalSetup() throws Exception;

    /**
     * Sets up this ExerciseInstance
     * <p/>
     * This method will be called on a all members of the cluster.
     *
     * @throws Exception
     */
    void localSetup() throws Exception;

    /**
     * Tears down this ExerciseInstance
     * <p/>
     * This method will  be called on a all members of the cluster.
     *
     * @throws Exception
     */
    void localTearDown() throws Exception;

    /**
     * Tears down this ExerciseInstance
     * <p/>
     * This method will only be called on a single member of the cluster.
     *
     * @throws Exception
     */
    void globalTearDown() throws Exception;

    void start() throws Exception;

    /**
     * Stops this ExerciseInstance.
     * <p/>
     * This method is synchronous, so after this method completes, the ExerciseInstance really should be stopped.
     *
     * @throws Exception
     */
    void stop() throws Exception;

    void localVerify() throws Exception;

    void globalVerify() throws Exception;
}
