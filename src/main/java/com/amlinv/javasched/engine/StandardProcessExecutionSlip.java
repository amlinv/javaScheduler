/*
 *   Copyright 2015 AML Innovation & Consulting LLC
 *
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

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

  @Override
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
