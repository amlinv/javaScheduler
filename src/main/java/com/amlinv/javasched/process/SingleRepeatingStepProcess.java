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

/**
 * Scheduler Process that repeats the same step on every request, optionally limited to execution
 * only when signaled.  An example use-case is aggregation of values that are periodically polled.
 *
 * Created by art on 6/5/15.
 */
public class SingleRepeatingStepProcess implements SchedulerProcess {
  private final Step repeatableStep;

  private final Object signalSync = new Object();

  private boolean repeatOnlyWhenSignalled = true;
  private long numSignal = 0;
  private long executionStartedSignalCount = 0;
  private WaitForSignalStep waitForSignalStep = new WaitForSignalStep();

  private boolean stopped = false;

  public SingleRepeatingStepProcess(Step repeatableStep, boolean repeatOnlyWhenSignalled) {
    this.repeatableStep = repeatableStep;
    this.repeatOnlyWhenSignalled = repeatOnlyWhenSignalled;
  }

  public SingleRepeatingStepProcess(Step repeatableStep) {
    this.repeatableStep = repeatableStep;
  }

  public boolean isRepeatOnlyWhenSignalled() {
    return repeatOnlyWhenSignalled;
  }

  public void setRepeatOnlyWhenSignalled(boolean repeatOnlyWhenSignalled) {
    this.repeatOnlyWhenSignalled = repeatOnlyWhenSignalled;
  }

  public void signal() {
    synchronized (this.signalSync) {
      this.numSignal++;
      this.signalSync.notifyAll();
    }
  }

  public void shutdown() {
    synchronized (this.signalSync) {
      this.stopped = true;
      this.signalSync.notifyAll();
    }
  }

  @Override
  public Step getNextStep() {
    if (this.stopped) {
      return null;
    }

    boolean repeatNow = false;
    if (this.repeatOnlyWhenSignalled) {
      if (this.numSignal > this.executionStartedSignalCount) {
        repeatNow = true;
      }
    } else {
      repeatNow = true;
    }

    Step result;
    if (repeatNow) {
      this.executionStartedSignalCount = this.numSignal;
      result = repeatableStep;
    } else {
      result = waitForSignalStep;
    }
    return result;
  }


  ////////////////////
  // INTERNAL CLASSES
  ////////////////////

  protected class WaitForSignalStep implements Step {
    SingleRepeatingStepProcess parent = SingleRepeatingStepProcess.this;

    @Override
    public void execute() {
      synchronized (parent.signalSync) {
        while ((parent.numSignal <= parent.executionStartedSignalCount) && (!parent.stopped)) {
          try {
            parent.signalSync.wait();
          } catch (InterruptedException intExc) {
            throw new RuntimeException("process interrupted", intExc);
          }
        }
      }
    }

    @Override
    public boolean isBlocking() {
      return true;
    }
  }
}
