package com.amlinv.javasched.impl;

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

  private boolean stopped = false;
  private boolean autoStop = false;

  public boolean isAutoStop() {
    return autoStop;
  }

  public void setAutoStop(boolean autoStop) {
    this.autoStop = autoStop;
  }

  public boolean isStopped() {
    return stopped;
  }

  @Override
  public Step getNextStep() {
    Step result;

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

  public void shutdown() {
    this.stopped = true;
    synchronized (this.queue) {
      this.queue.notifyAll();
    }
  }

  public void waitUntilFinished() throws InterruptedException {
    synchronized (this.queue) {
      while (!this.stopped) {
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
            parent.queue.wait();
          } catch (InterruptedException intExc) {
            log.info("wait for more work interrupted; checking for more work and shutdown now");

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
