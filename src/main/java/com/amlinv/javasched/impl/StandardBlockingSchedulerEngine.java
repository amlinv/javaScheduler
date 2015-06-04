package com.amlinv.javasched.impl;

import com.amlinv.javasched.BlockingSchedulerEngine;
import com.amlinv.javasched.Step;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standard scheduler of blocking steps.
 *
 * Created by art on 12/8/14.
 */
public class StandardBlockingSchedulerEngine implements BlockingSchedulerEngine {

  private static final Logger DEFAULT_LOGGER = LoggerFactory
      .getLogger(StandardBlockingSchedulerEngine.class);

  private Logger log = DEFAULT_LOGGER;

  public static final int DEFAULT_MAXIMUM_PROCESSORS = 1000;

  /**
   * Maximum number of processing threads that may be "executing" blocking tasks.  These threads
   * will be mostly blocking, so really sitting and using resources for little purpose, but setting
   * this number too low means that blocking steps may backlog, so that their processing is not
   * initiated until other blocking steps finally complete.
   */
  private int maximumProcessors = DEFAULT_MAXIMUM_PROCESSORS;
  private int numStartedThread = 0;
  private int numIdleThread = 0;
  private long maxThreadIdleTime;
  private long nextThreadNum = 0;
  private AtomicLong totalStepsStarted = new AtomicLong(0);
  private Object processorSync = new Object();

  private final ConcurrentLinkedQueue<Step> backlog = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<ProcessorThread> idleThreads = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<ProcessorThread> allocThreads = new ConcurrentLinkedQueue<>();

  private boolean started = false;
  private boolean stopped = false;

  /**
   * Validation hooks for testing; please do not use these outside of unit test of this class!
   * These hooks eliminate the need for sleeps and eliminate potential race conditions.
   */
  protected ConcurrencyValidationHooks hooks = new ConcurrencyValidationHooks();

  public int getMaximumProcessors() {
    return this.maximumProcessors;
  }

  public void setMaximumProcessors(int newMaximumProcessors) {
    this.maximumProcessors = newMaximumProcessors;
  }

  /**
   * Return the maximum thread idle time, in milliseconds.
   *
   * @return maximum thread idle time, in milliseconds.
   */
  public long getMaxThreadIdleTime() {
    return maxThreadIdleTime;
  }

  public void setMaxThreadIdleTime(long newMaxIdle, TimeUnit timeUnit) {
    this.maxThreadIdleTime = TimeUnit.MILLISECONDS.convert(newMaxIdle, timeUnit);
  }

  public Logger getLog() {
    return log;
  }

  public void setLog(Logger log) {
    this.log = log;
  }

  public int getNumStartedThread() {
    return numStartedThread;
  }

  public int getNumIdleThread() {
    return numIdleThread;
  }

  public long getTotalStepsStarted() {
    return totalStepsStarted.get();
  }

  public void initiateShutdown(boolean interruptThreads) {
    this.stopped = true;

    //
    // Interrupt all of the threads now.
    //
    if (interruptThreads) {
      for ( ProcessorThread oneThread : allocThreads ) {
        oneThread.interrupt();
      }
    }

    //
    // Stop the idle threads, in case they are still waiting.
    //
    ProcessorThread idleThread = idleThreads.poll();
    while (idleThread != null) {
      synchronized (this.processorSync) {
        this.numIdleThread--;
      }

      synchronized (idleThread) {
        idleThread.notifyAll();
      }

      idleThread = idleThreads.poll();
    }
  }

  @Override
  public void start() {
    synchronized (this.processorSync) {
      if (this.started) {
        throw new IllegalStateException("already started");
      }

      if (maximumProcessors < 1) {
        throw new IllegalStateException(
            "scheduler engine needs a positive number of processors to start");
      }

      this.started = true;
    }
  }

  @Override
  public void submit(Step blockingStep) {
    synchronized (this.processorSync) {
      if (!this.started) {
        throw new IllegalStateException("not yet started");
      }

      if (this.stopped) {
        throw new IllegalStateException("scheduler has already stopped");
      }
    }

    //
    // Attempt to grab an idle thread.
    //
    ProcessorThread execThread = this.idleThreads.poll();

    if (execThread != null) {
      synchronized (this.processorSync) {
        this.numIdleThread--;
      }

      //
      // Use the idle thread.
      //
      execThread.setNextStep(blockingStep);
      this.totalStepsStarted.incrementAndGet();
    } else {
      //
      // No idle thread was grabbed.  See if another thread may be allocated.
      //
      boolean available = true;
      long threadNum = -1;

      synchronized (this.processorSync) {
        if (this.numStartedThread < this.maximumProcessors) {
          // Update the count now to make sure the check-and-set are atomic even though the thread
          //  won't actual start until later.
          this.numStartedThread++;

          threadNum = this.nextThreadNum;
          this.nextThreadNum++;
        } else {
          available = false;
        }
      }

      //
      // If a new thread may be started, start one now.
      //
      if (available) {
        execThread = new ProcessorThread(threadNum);

        this.hooks.onNewProcessorThread();

        synchronized (this.processorSync) {
          if (this.stopped) {
            throw new IllegalStateException("scheduler has already stopped");
          }

          this.allocThreads.add(execThread);
        }

        execThread.setNextStep(blockingStep);
        execThread.start();

        this.totalStepsStarted.incrementAndGet();
      } else {
        //
        // No more threads; add this step to the backlog.
        //
        this.backlog.add(blockingStep);

        this.hooks.onTaskAddedToBacklog();
      }
    }
  }


  /**
   * Grab the next available step from the backlog, if any, without blocking.
   *
   * @param threadNumber number of the thread requesting the next backlog step.
   * @return the next step, extracted from the backlog, if any; null if none is waiting.
   */
  protected Step grabNextStep(long threadNumber) {
    Step step = this.backlog.poll();

    if (step != null) {
      this.totalStepsStarted.incrementAndGet();
    }

    return step;
  }

  /**
   * On detecting a thread is idle (i.e. has no next step to perform), add the thread to the idle
   * pool.
   *
   * @param idleThread processing thread to add to the idle pool.
   */
  protected void onIdleThread(ProcessorThread idleThread) {
    synchronized (this.processorSync) {
      this.idleThreads.add(idleThread);
      this.numIdleThread++;
    }

    this.hooks.onThreadNowIdle();
  }

  /**
   * Handle a thread that has reached the idle timeout, meaning it is time for the thread to
   * shutdown.
   *
   * @param idleThread the thread that has been idle beyond the threshold for idle timeout.
   * @return true => if the idle thread was ejected and the thread should shutdown now; false => if
   * the idle thread is in the process of being activated and should continue operating.
   */
  protected boolean oneIdleThreadTimeout(ProcessorThread idleThread) {
    if (this.stopped) {
      return true;
    }

    //
    // Remove the thread from the list of idle threads.  This can be a slow operation, so keep it
    //  outside the critical section.  If it is successfully removed, then update the idle thread
    //  count.
    //
    boolean removed = this.idleThreads.remove(idleThread);

    if (removed) {
      synchronized (this.processorSync) {
        //
        // Update the thread counts.  This is one less started thread, and one less idle thread.
        //
        this.numStartedThread--;
        this.numIdleThread--;
      }

      this.hooks.onIdleThreadRemoved();
    }

    return removed;
  }

  protected void onProcessorThreadCompletion(ProcessorThread completedThread) {
    this.allocThreads.remove(completedThread);
  }

  /**
   * Processor Thread that actually executes steps and waits for more steps to execute.
   */
  protected class ProcessorThread extends Thread {

    private final StandardBlockingSchedulerEngine parent = StandardBlockingSchedulerEngine.this;
    private final long threadNumber;

    private boolean running = true;
    private Step nextStep;

    /**
     * Create the processor thread with the given thread number.
     */
    public ProcessorThread(long threadNumber) {
      super("standard-blocking-scheduler-processor-thread#" + threadNumber);

      this.threadNumber = threadNumber;
    }

    /**
     * Set the next step to be executed by this processor thread.  Should only be called when the
     * thread is known to be idle.
     *
     * @param newNextStep next step to be executed.
     */
    public void setNextStep(Step newNextStep) {
      this.nextStep = newNextStep;

      synchronized (this) {
        this.notifyAll();
      }
    }

    /**
     * Main loop that grabs a step and executes it while also checking for idle timeout.
     */
    @Override
    public void run() {
      try {
        //
        // Loop until it is time to shutdown this thread.
        //
        while (this.running) {
          Step activeStep;

          //
          // Execute the next step injected, if one exists.
          //
          if (this.nextStep != null) {
            activeStep = this.nextStep;
            this.nextStep = null;
          } else {
            //
            // No step was injected (this thread must have just finished another step); pick up the
            //  next step from the backlog, if any.
            //
            activeStep = parent.grabNextStep(this.threadNumber);
          }

          //
          // Process the step retrieved, if found; otherwise, wait.
          //
          if (activeStep != null) {
            hooks.onThreadStartingNewStep();

            //
            // Execute and log any exception thrown.  Double-check for scheduler shutdown first
            //  though.
            //
            try {
              if (!parent.stopped) {
                activeStep.execute();
              } else {
                this.running = false;
              }
            } catch (Exception exc) {
              parent.log.warn("failed step execution", exc);
            }
          } else {
            //
            // Let the scheduler know this thread is idle and ready for a new step.
            //
            parent.onIdleThread(this);

            synchronized (this) {
              //
              // Wait up to the idle period; double-check with the lock held, though, that there is
              //  no step injected in case of a race.
              //
              if (this.nextStep == null) {
                try {
                  this.wait(parent.maxThreadIdleTime);
                } catch (InterruptedException intExc) {
                  parent.log
                      .debug("idle thread interrupted waiting for step; processing as timeout");
                }
              }
            }

            //
            // If no next step is defined by this time, signal the scheduler that this thread is
            //  about to terminate.
            //
            boolean timeToStop = false;
            if (this.nextStep == null) {
              timeToStop = parent.oneIdleThreadTimeout(this);
            }

            //
            // If the idle timeout processing did eject the thread from the idle pool, then it is
            //  time to stop; otherwise, there should be a step ready for execution now.
            //
            if (timeToStop) {
              this.running = false;
            }
          }
        }
      } finally {
        parent.onProcessorThreadCompletion(this);
      }
    }
  }

  /**
   * Validation hooks for testing this class; please do not use these outside of unit tests for the
   * parent class!
   */
  protected static class ConcurrencyValidationHooks {
    public void onThreadNowIdle() {
    }

    public void onIdleThreadRemoved() {
    }

    public void onTaskAddedToBacklog() {
    }

    public void onNewProcessorThread() {
    }

    public void onThreadStartingNewStep() {
    }
  }
}
