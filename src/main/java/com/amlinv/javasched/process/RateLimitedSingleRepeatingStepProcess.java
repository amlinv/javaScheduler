package com.amlinv.javasched.process;

import com.amlinv.javasched.SchedulerProcess;
import com.amlinv.javasched.Step;

/**
 * Single repeating step process that additionally limits the rate at which steps are executed.
 * An example use case is aggregation of metrics collected periodically where there is a need to
 * limit the overall overhead of updating the aggregate.  Another use-case is sending a rate-
 * limited ping, health-check, or status over a connection to a client, server, or peer.
 *
 * Created by art on 6/5/15.
 */
public class RateLimitedSingleRepeatingStepProcess extends SingleRepeatingStepProcess {
  private final long ratePeriod;

  private long lastMark = 0;

  private final WaitRatePeriodCompleteStep waitRatePeriodCompleteStep =
      new WaitRatePeriodCompleteStep();

  public RateLimitedSingleRepeatingStepProcess(Step repeatableStep, boolean repeatOnlyWhenSignalled,
                                               long ratePeriod, long initialDelay) {
    super(repeatableStep, repeatOnlyWhenSignalled);
    this.ratePeriod = ratePeriod;
    this.lastMark = ( System.nanoTime() / 1000000L ) + initialDelay - ratePeriod;
  }

  public RateLimitedSingleRepeatingStepProcess(Step repeatableStep, long ratePeriod,
                                               long initialDelay) {
    super(repeatableStep);
    this.ratePeriod = ratePeriod;
    this.lastMark = ( System.nanoTime() / 1000000L ) + initialDelay - ratePeriod;
  }

  @Override
  public Step getNextStep() {
    long mark = System.nanoTime() / 1000000L;

    Step result;
    if ((mark - this.lastMark) >= this.ratePeriod) {
      this.lastMark = mark;
      result = super.getNextStep();
    } else {
      result = this.waitRatePeriodCompleteStep;
    }

    return  result;
  }

  ////////////////////
  // INTERNAL CLASSES
  ////////////////////

  protected class WaitRatePeriodCompleteStep implements Step {
    RateLimitedSingleRepeatingStepProcess parent = RateLimitedSingleRepeatingStepProcess.this;

    @Override
    public void execute() {
      long mark = System.nanoTime() / 1000000L;
      long diff = mark - parent.lastMark;

      if (diff < parent.ratePeriod) {
        try {
          Thread.sleep(parent.ratePeriod - diff);
        } catch (InterruptedException intExc) {
          throw new RuntimeException("process interrupted", intExc);
        }
      }
    }

    @Override
    public boolean isBlocking() {
      return true;
    }
  }
}
