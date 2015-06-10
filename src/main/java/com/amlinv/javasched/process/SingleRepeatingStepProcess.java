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
