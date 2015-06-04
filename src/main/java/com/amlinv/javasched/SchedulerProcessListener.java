package com.amlinv.javasched;

/**
 * Listener for scheduler events on a SchedulerProcess.
 *
 * Created by art on 12/8/14.
 */
public interface SchedulerProcessListener {

  /**
   * Synchronous method listener call when a process step is started; make sure this executes very quickly as it is in
   * the direct path of execution of the scheduler.
   */
  void  stepStarted(Step step);

  /**
   * Synchronous method listener call when a process step completes; make sure this executes very quickly as it is in
   * the direct path of execution of the scheduler.
   */
  void  stepComplete(Step step);

  /**
   * Synchronous method listener call when a process step fails; make sure this executes very quickly as it is in the
   * direct path of execution of the scheduler.
   */
  void  stepFailure(Step step, Exception exc);

  /**
   * Synchronous method listener call when a process completes; make sure this executes very quickly as it is in the
   * direct path of execution of the scheduler.
   */
  void  processComplete();
}
