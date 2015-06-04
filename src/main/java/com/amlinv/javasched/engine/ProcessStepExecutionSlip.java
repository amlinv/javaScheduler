package com.amlinv.javasched.engine;

import com.amlinv.javasched.Step;

/**
 * A step wrapped for the scheduler so that it can detect when step execution has completed in
 * order to iterate the original process onto its next step.
 *
 * Created by art on 6/3/15.
 */
public class ProcessStepExecutionSlip implements Step {
  private final Step sourceStep;
  private final ProcessStepExecutionListener listener;

  public ProcessStepExecutionSlip(Step initSourceStep, ProcessStepExecutionListener initListener) {
    this.sourceStep = initSourceStep;
    this.listener = initListener;
  }

  @Override
  public void execute() {
    this.listener.onStepStarted();
    try {
      this.sourceStep.execute();
      this.listener.onStepStopped();
    } catch ( RuntimeException rtExc ) {
      this.listener.onStepException(rtExc);
      throw rtExc;
    }
  }

  @Override
  public boolean isBlocking() {
    return this.sourceStep.isBlocking();
  }
}
