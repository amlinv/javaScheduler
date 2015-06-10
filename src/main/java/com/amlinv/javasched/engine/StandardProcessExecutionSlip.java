package com.amlinv.javasched.engine;

import com.amlinv.javasched.SchedulerProcess;
import com.amlinv.javasched.SchedulerProcessExecutionSlip;

/**
 * Process execution slip that can be used to detect the state of a running process.
 *
 * Created by art on 12/14/14.
 */
public class StandardProcessExecutionSlip
    implements SchedulerProcessExecutionSlip, ProcessStateListener {

  /**
   * The process is runnable when it has been submitted to a scheduler, until the scheduler
   * determines the process is complete.
   */
  private boolean runnable;

  /**
   * A started process is one that the scheduler has accepted and became ready to execute.  The
   * process may no longer be runnable, so this only marks a one-time event.
   */
  private boolean started;

  /**
   * A stopped process is one that the scheduler executed to completion (i.e. until it produced no
   * more steps).
   */
  private boolean stopped;

  private final SchedulerProcess schedulerProcess;
  private final Object stateSync = new Object();

  private ValidationHooks validationHooks = new ValidationHooks();

  public StandardProcessExecutionSlip(SchedulerProcess theSchedulerProcess) {
    this.runnable = true;
    this.started = false;
    this.stopped = false;
    this.schedulerProcess = theSchedulerProcess;
  }

  public SchedulerProcess getSchedulerProcess() {
    return schedulerProcess;
  }

  /**
   * Set validation hooks to the ones given; this is intended for testing purposes only.
   *
   * @param validationHooks new
   */
  public void setValidationHook(ValidationHooks validationHooks) {
    this.validationHooks = validationHooks;
  }

  @Override
  public void waitUntilComplete() throws InterruptedException {
    this.waitUntilComplete(0, 0);
  }

  @Override
  public void waitUntilComplete(long timeoutMilli, long timeoutNano) throws InterruptedException {
    long endMark;
    boolean expired = false;

    if ((timeoutMilli != 0) || (timeoutNano != 0)) {
      endMark = System.nanoTime() + (timeoutNano + (timeoutMilli * 1000000L));
    } else {
      endMark = 0;
    }

    synchronized (this.stateSync) {
      while ((this.runnable) && (!expired)) {
        if (endMark != 0) {
          long now = System.nanoTime();
          if (now < endMark) {
            long remaining = endMark - now;
            this.validationHooks.onStartWaitForCompletion();
            this.stateSync.wait(remaining / 1000000L, (int) (remaining % 1000000L));
          } else {
            expired = true;
            this.validationHooks.onWaitForCompleteTimeoutExpired();
          }
        } else {
          this.validationHooks.onStartWaitForCompletion();
          this.stateSync.wait();
        }
      }
    }
  }

  @Override
  public boolean isRunnable() {
    return this.runnable;
  }

  @Override
  public void processStarted() {
    synchronized (this.stateSync) {
      this.started = true;
      this.runnable = true;
      this.stateSync.notifyAll();
    }
  }

  /**
   * Notify when a process has stopped running.
   */
  @Override
  public void processStopped() {
    synchronized (this.stateSync) {
      this.stopped = true;
      this.runnable = false;
      this.stateSync.notifyAll();
    }
  }

  ////////////////////
  // INTERNAL CLASSES
  ////////////////////

  protected class ValidationHooks {
    public void onStartWaitForCompletion() {
    }

    public void onWaitForCompleteTimeoutExpired() {
    }
  }
}