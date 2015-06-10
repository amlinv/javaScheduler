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

package com.amlinv.javasched.impl;

import com.amlinv.javasched.BlockingSchedulerEngine;
import com.amlinv.javasched.NonBlockingSchedulerEngine;
import com.amlinv.javasched.Scheduler;
import com.amlinv.javasched.SchedulerProcess;
import com.amlinv.javasched.SchedulerProcessExecutionSlip;
import com.amlinv.javasched.Step;
import com.amlinv.javasched.engine.ProcessStepExecutionListener;
import com.amlinv.javasched.engine.ProcessStepExecutionSlipFactory;
import com.amlinv.javasched.engine.StandardProcessExecutionSlip;
import com.amlinv.javasched.engine.StandardProcessExecutionSlipFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Scheduler of processes, each of which is a serialized source of processing steps in which each
 * step can be blocking or non-blocking.  Steps are fed into the blocking and non-blocking engines
 * based on the type of step, and processes iterate over steps until no more steps are available, or
 * one of the steps has a fatal error.
 *
 * Created by art on 12/10/14.
 */
public class RoundRobinScheduler implements Scheduler {

  //  private final Set<StandardProcessExecutionSlip> processSet;
  private final Map<SchedulerProcess, StandardProcessExecutionSlip> processes;
  private final Object schedulerSync = new Object();

  private NonBlockingSchedulerEngine nonBlockingEngine = new StandardNonBlockingSchedulerEngine();
  private BlockingSchedulerEngine blockingEngine = new StandardBlockingSchedulerEngine();

  private boolean started;

  private ProcessStepExecutionSlipFactory stepExecutionSlipFactory;
  private StandardProcessExecutionSlipFactory processExecutionSlipFactory;

  public RoundRobinScheduler() {
    this.processes = new HashMap<>();
    this.started = false;
    this.stepExecutionSlipFactory = new ProcessStepExecutionSlipFactory();
    this.processExecutionSlipFactory = new StandardProcessExecutionSlipFactory();
  }

  public NonBlockingSchedulerEngine getNonBlockingEngine() {
    return this.nonBlockingEngine;
  }

  public void setNonBlockingEngine(NonBlockingSchedulerEngine newNonBlockingEngine) {
    this.nonBlockingEngine = newNonBlockingEngine;
  }

  public BlockingSchedulerEngine getBlockingEngine() {
    return blockingEngine;
  }

  public void setBlockingEngine(BlockingSchedulerEngine newEngine) {
    this.blockingEngine = newEngine;
  }

  public ProcessStepExecutionSlipFactory getStepExecutionSlipFactory() {
    return stepExecutionSlipFactory;
  }

  public void setStepExecutionSlipFactory(
      ProcessStepExecutionSlipFactory stepExecutionSlipFactory) {
    this.stepExecutionSlipFactory = stepExecutionSlipFactory;
  }

  @Override
  public void start() {
    synchronized (this.schedulerSync) {
      if (this.started) {
        throw new IllegalStateException("already started");
      }
      this.started = true;
    }

    this.nonBlockingEngine.start();
    this.blockingEngine.start();
  }

  @Override
  public SchedulerProcessExecutionSlip startProcess(SchedulerProcess newProcess) {
    synchronized (schedulerSync) {
      if (!this.started) {
        throw new IllegalStateException("not yet started");
      }
    }

    StandardProcessExecutionSlip executionSlip = this.prepareProcessExecutionSlip(newProcess);

    boolean previouslySubmitted;
    synchronized (this.processes) {
      previouslySubmitted = this.processes.containsKey(newProcess);
      if (!previouslySubmitted) {
        this.processes.put(newProcess, executionSlip);
      }
    }

    if (previouslySubmitted) {
      throw new IllegalStateException("process is already active");
    }

    executionSlip.processStarted();
    this.advanceProcessExecution(newProcess);

    return executionSlip;
  }

  @Override
  public List<SchedulerProcess> getProcessList() {
    LinkedList<SchedulerProcess> result;
    synchronized (this.processes) {
      result = new LinkedList<SchedulerProcess>(this.processes.keySet());
    }

    return result;
  }

  protected void onProcessCompletion(SchedulerProcess process) {
    StandardProcessExecutionSlip executionSlip;
    synchronized (this.processes) {
      executionSlip = this.processes.remove(process);
    }

    if (executionSlip != null) {
      executionSlip.processStopped();
    }
  }

  protected void advanceProcessExecution(SchedulerProcess process) {
    Step nextStep = process.getNextStep();

    if (nextStep == null) {
      //
      // No more steps for this process; cleanup.
      //
      this.onProcessCompletion(process);
    } else {
      //
      // Prepare an execution slip that will notify back the state of the step's execution.
      //
      Step executionSlip = this.prepareStepExecutionSlip(process, nextStep);

      //
      // Hand-off to the blocking or non-blocking engine based on whether the step is blocking or
      //  non-blocking.
      //
      if (nextStep.isBlocking()) {
        this.blockingEngine.submit(executionSlip);
      } else {
        this.nonBlockingEngine.submit(executionSlip);
      }
    }
  }

  protected void onProcessStepException(SchedulerProcess process, Throwable exc) {
    this.onProcessCompletion(process);
  }

  protected StandardProcessExecutionSlip prepareProcessExecutionSlip(
      final SchedulerProcess theProcess) {

    return this.processExecutionSlipFactory.createSlip(theProcess);
  }

  protected Step prepareStepExecutionSlip(SchedulerProcess process, Step step) {
    ProcessStepExecutionListener listener = this.prepareStepExecutionListener(process);

    Step result = this.stepExecutionSlipFactory.createProcessStepExecutionSlip(step, listener);

    return result;
  }

  /**
   * Prepare a process step execution listener that will transition the process to the next state in
   * its execution.
   *
   * @param process the process for which a step will be executed.
   * @return the new listener that will advance the state of the given process on completion of the
   * current step.
   */
  protected ProcessStepExecutionListener prepareStepExecutionListener(
      final SchedulerProcess process) {

    return new ProcessStepExecutionListener() {
      private RoundRobinScheduler parent = RoundRobinScheduler.this;

      @Override
      public void onStepStarted() {
      }

      @Override
      public void onStepStopped() {
        //
        // Last step finished; advance the process execution to the next step.
        //
        parent.advanceProcessExecution(process);
      }

      @Override
      public void onStepException(Throwable exc) {
        //
        // Fatal exception on processing the step; cleanup.
        //
        parent.onProcessStepException(process, exc);
      }
    };
  }
}
