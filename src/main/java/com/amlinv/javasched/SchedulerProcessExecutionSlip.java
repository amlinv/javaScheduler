package com.amlinv.javasched;

/**
 * Slip recording the execution of a process that can be used to check the state of execution.
 *
 * Created by art on 12/14/14.
 */
public interface SchedulerProcessExecutionSlip {
  void    waitUntilComplete() throws InterruptedException;
  void    waitUntilComplete(long timeoutMilli, long timeoutNano) throws InterruptedException;
  boolean isRunnable();
}
