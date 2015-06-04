package com.amlinv.javasched.engine;

import com.amlinv.javasched.Step;

/**
 * Created by art on 6/3/15.
 */
public class ProcessStepExecutionSlipFactory {
  public ProcessStepExecutionSlip createProcessStepExecutionSlip(Step initSourceStep,
                                                                 ProcessStepExecutionListener initListener) {

    return new ProcessStepExecutionSlip(initSourceStep, initListener);
  }
}
