package com.amlinv.javasched.impl;

import com.amlinv.javasched.BlockingSchedulerEngine;
import com.amlinv.javasched.NonBlockingSchedulerEngine;
import com.amlinv.javasched.Scheduler;
import com.amlinv.javasched.SchedulerProcess;
import com.amlinv.javasched.SchedulerProcessExecutionSlip;
import com.amlinv.javasched.Step;
import com.amlinv.javasched.engine.ProcessStepExecutionListener;
import com.amlinv.javasched.engine.ProcessStepExecutionSlipFactory;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Scheduler of processes, each of which is a serialized source of processing steps in which each
 * step can be blocking or non-blocking.  Steps are fed into the blocking and non-blocking engines
 * based on the type of step, and processes iterate over steps until no more steps are available,
 * or one of the steps has a fatal error.
 *
 * Created by art on 12/10/14.
 */
public class StandardScheduler implements Scheduler {

  private final Set<SchedulerProcess> processSet;
  private final Object schedulerSync = new Object();

  private NonBlockingSchedulerEngine nonBlockingEngine = new StandardNonBlockingSchedulerEngine();
  private BlockingSchedulerEngine blockingEngine = new StandardBlockingSchedulerEngine();

  private boolean started;

  private ProcessStepExecutionSlipFactory executionSlipFactory;

  public StandardScheduler() {
    this.processSet = new HashSet<>();
    this.started = false;
    this.executionSlipFactory = new ProcessStepExecutionSlipFactory();
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

  public ProcessStepExecutionSlipFactory getExecutionSlipFactory() {
    return executionSlipFactory;
  }

  public void setExecutionSlipFactory(ProcessStepExecutionSlipFactory executionSlipFactory) {
    this.executionSlipFactory = executionSlipFactory;
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

    boolean notPreviouslySubmitted;
    synchronized (this.processSet) {
      notPreviouslySubmitted = this.processSet.add(newProcess);
    }

    if (!notPreviouslySubmitted) {
      throw new IllegalStateException("process is already active");
    }

    this.advanceProcessExecution(newProcess);

    // TBD
    return null;
  }

  @Override
  public List<SchedulerProcess> getProcessList() {
    LinkedList<SchedulerProcess> result;
    synchronized (this.processSet) {
      result = new LinkedList<SchedulerProcess>(this.processSet);
    }

    return result;
  }

  protected void onProcessCompletion(SchedulerProcess process) {
    synchronized (this.processSet) {
      this.processSet.remove(process);
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

  protected Step prepareStepExecutionSlip(SchedulerProcess process, Step step) {
    ProcessStepExecutionListener listener = this.prepareStepExecutionListener(process);

    Step result = this.executionSlipFactory.createProcessStepExecutionSlip(step, listener);

    return result;
  }

  /**
   * Prepare a process step execution listener that will transition the process to the next state
   * in its execution.
   *
   * @param process the process for which a step will be executed.
   * @return the new listener that will advance the state of the given process on completion of
   * the current step.
   */
  protected ProcessStepExecutionListener prepareStepExecutionListener(
      final SchedulerProcess process) {

    return new ProcessStepExecutionListener() {
      private StandardScheduler parent = StandardScheduler.this;

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
