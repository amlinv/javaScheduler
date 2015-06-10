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

package com.amlinv.javasched.process;

import com.amlinv.javasched.SchedulerProcess;
import com.amlinv.javasched.Step;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.LinkedList;

/**
 * A scheduler process that operates off a simple list of steps and provides a blocking step that
 * waits for new steps to be added until the process is terminated.
 *
 * Created by art on 6/4/15.
 */
public class StepListSchedulerProcess implements SchedulerProcess {

  private static final Logger DEFAULT_LOG = LoggerFactory.getLogger(StepListSchedulerProcess.class);

  private Logger log = DEFAULT_LOG;

  private final Deque<Step> queue = new LinkedList<>();

  private final Step waitMoreWorkStep = new WaitForMoreWorkStep();

  private volatile boolean stopped = false;
  private boolean autoStop = false;

  private ValidationHook validationHook = new ValidationHook();

  public boolean isAutoStop() {
    return autoStop;
  }

  public void setAutoStop(boolean autoStop) {
    this.autoStop = autoStop;
  }

  public boolean isStopped() {
    return stopped;
  }

  public void setLog(Logger log) {
    this.log = log;
  }

  public void setValidationHook(ValidationHook validationHook) {
    this.validationHook = validationHook;
  }

  @Override
  public Step getNextStep() {
    Step result;

    if (stopped) {
      return null;
    }

    synchronized (this.queue) {
      result = this.queue.poll();

      if ((result == null) && (autoStop)) {
        log.debug("auto-stop set and no more steps defined; shutting down");
        this.shutdown();
      }
    }

    /**
     * If no step was retrieved from the queue and we are still active, use the wait step.
     */
    if ((result == null) && (!this.stopped)) {
      result = this.waitMoreWorkStep;
    }

    return result;
  }

  public void addStep(Step newStep) {
    synchronized (this.queue) {
      this.queue.add(newStep);
      this.queue.notifyAll();
    }
  }

  /**
   * Return the number of steps thare are queued, waiting to be started.
   *
   * @return the number of queued steps.
   */
  public int getPendingStepCount() {
    int result;
    synchronized (this.queue) {
      result = this.queue.size();
    }
    return result;
  }

  public void shutdown() {
    this.stopped = true;
    synchronized (this.queue) {
      this.queue.notifyAll();
    }
  }

  public void waitUntilFinished() throws InterruptedException {
    synchronized (this.queue) {
      while (!this.stopped) {
        this.validationHook.onWaitOnQueue();
        this.queue.wait();
      }
    }
  }

  //////////////////////////////////////////////////
  //
  // STEP that waits for more steps to be added
  //
  //////////////////////////////////////////////////

  protected class WaitForMoreWorkStep implements Step {

    StepListSchedulerProcess parent = StepListSchedulerProcess.this;

    @Override
    public void execute() {
      synchronized (parent.queue) {
        while (parent.queue.isEmpty() && (!parent.stopped)) {
          try {
            parent.validationHook.onWaitOnQueue();
            parent.queue.wait();
          } catch (InterruptedException intExc) {
            log.info("wait for more work interrupted; checking for more work and possible shutdown now");
          }
        }
      }
    }

    @Override
    public boolean isBlocking() {
      return true;
    }
  }

  protected static class ValidationHook {
    public void onWaitOnQueue() throws InterruptedException {
    }
  }
}
