package com.amlinv.javasched.impl;

import com.amlinv.javasched.NonBlockingSchedulerEngine;
import com.amlinv.javasched.Step;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by art on 12/8/14.
 */
public class StandardNonBlockingSchedulerEngine implements NonBlockingSchedulerEngine {

  private static final Logger DEFAULT_LOGGER = LoggerFactory
      .getLogger(StandardNonBlockingSchedulerEngine.class);

  private Logger log = DEFAULT_LOGGER;

  private int processorCount;

  private Object processorSync = new Object();
  private ProcessorThread[] processorThreads;
  private boolean started;
  private boolean stopped;

  private LinkedBlockingQueue<Step> stepQueue = new LinkedBlockingQueue<>();

  ////////////////////////
  //
  // PUBLIC METHODS
  //
  ////////////////////////

  /**
   * Create the non-blocking scheduler engine using the given number of processors.
   *
   * @param processorCount number of processor threads to use for execution of tasks.
   */
  public StandardNonBlockingSchedulerEngine(int processorCount) {
    this.processorCount = processorCount;
  }

  /**
   * Create the non-blocking scheduler engine with auto-detection of the number of system
   *  processors using Runtime.availableProcessors().  Creates one less processing thread than
   *  the number of detected system processors so one processor is always available for scheduler
   *  and other activities, unless the system processor count is 1, in which case the processor
   *  count falls-back to 1.
   *
   *  Process count is aut-detected using Runtime.availableProcessors().
   */
  public StandardNonBlockingSchedulerEngine() {
    int numSysProcessor = Runtime.getRuntime().availableProcessors();
    this.processorCount = Math.max(numSysProcessor - 1, 1);
  }

  public int getProcessorCount() {
    return this.processorCount;
  }

  public void setProcessorCount(int newNumProcessor) {
    this.processorCount = newNumProcessor;
  }

  public Logger getLog() {
    return log;
  }

  public void setLog(Logger log) {
    this.log = log;
  }

  @Override
  public void start() {
    synchronized (this.processorSync) {
      if (this.started) {
        throw new IllegalStateException("already started");
      }

      if (this.processorCount < 1) {
        throw new IllegalStateException("processor count must be positive");
      }

      this.started = true;
    }

    //
    // Allocate all the processor threads and start them.
    //
    this.processorThreads = new ProcessorThread[this.processorCount];

    int iter = 0;
    while (iter < this.processorCount) {
      this.processorThreads[iter] = new ProcessorThread(iter);
      this.processorThreads[iter].start();

      iter++;
    }
  }

  public void initiateShutdown() {
    synchronized (this.processorSync) {
      if (!this.started) {
        throw new IllegalStateException("not yet started");
      }

      if (this.stopped) {
        throw new IllegalStateException("already stopped");
      }

      this.stopped = true;
    }

    for (ProcessorThread processorThread : processorThreads) {
      processorThread.interrupt();
    }
  }

  @Override
  public void submit(Step nonBlockingStep) {
    if (!this.started) {
      throw new IllegalStateException("not yet started");
    }

    if (this.stopped) {
      throw new IllegalStateException("scheduler already stopped");
    }

    if ( nonBlockingStep == null ) {
      throw new NullPointerException();
    }

    boolean added = this.stepQueue.offer(nonBlockingStep);
    if (!added) {
      throw new RuntimeException("internal error: unbounded queue of steps rejected offered step");
    }
  }


  ////////////////////////
  //
  // INTERNAL METHODS
  //
  ////////////////////////

  protected void injectQueueForTest(LinkedBlockingQueue<Step> injectQueue) {
    this.stepQueue = injectQueue;
  }

  /**
   * Grab the next step, waiting if necessary.
   *
   * @param threadNum number of the processor thread waiting for the step.
   * @return the next step to execute.
   */
  protected Step waitForStep(int threadNum) throws InterruptedException {
    Step nextStep;

    nextStep = this.stepQueue.take();

    return nextStep;
  }

  protected class ProcessorThread extends Thread {
    private StandardNonBlockingSchedulerEngine parent = StandardNonBlockingSchedulerEngine.this;
    private int threadNumber;
    private boolean running = true;

    public ProcessorThread(int threadNumber) {
      super("standard-non-blocking-scheduler-processor-thread#" + threadNumber);
      this.threadNumber = threadNumber;
    }

    @Override
    public void run() {
      while (this.running && (!parent.stopped)) {
        try {
          Step execStep = parent.waitForStep(this.threadNumber);

          try {
            execStep.execute();
          } catch (Exception exc) {
            parent.log.warn("failed step execution", exc);
          }
        } catch (InterruptedException intExc) {
          parent.log.info("stopping processor thread #{} on interrupt", this.threadNumber);
          this.running = false;
        }
      }
    }
  }
}
