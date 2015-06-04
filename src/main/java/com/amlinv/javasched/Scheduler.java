package com.amlinv.javasched;

import java.util.List;

/**
 * Scheduler - the top-level object for running processes.  Processes are submitted to the scheduler
 * which then starts each process by asking for the first step.  Non-blocking steps are immediately
 * queued for execution on a non- blocking thread executor which runs a small number of threads that
 * are intended to be running non-stop except when there are no non-blocking steps ready for
 * execution.
 *
 * Blocking steps, on the other hand, are queued for execution on the blocking-thread executor which
 * will allocate large numbers of threads, as-needed, to sit and wait for blocking operations to
 * process.  Obviously, it's best to use IOC and asynchronous methods to avoid blocking, but that is
 * beyond the scope of the scheduler itself.
 *
 * Created by art on 12/8/14.
 */
public interface Scheduler {

  /**
   * Start execution of the given process.
   *
   * @param newProcess process to execute.
   * @return execution slip that can be used to watch the progress and status of the process.
   */
  SchedulerProcessExecutionSlip startProcess(SchedulerProcess newProcess);

  /**
   * Retrieve the list of processes currently being scheduled.
   *
   * @return list of the scheduled processes.
   */
  List<SchedulerProcess> getProcessList();

  /**
   * Start the scheduler main loop.
   */
  void start();
}
