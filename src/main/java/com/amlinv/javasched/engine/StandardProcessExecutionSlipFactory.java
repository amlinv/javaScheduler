package com.amlinv.javasched.engine;

import com.amlinv.javasched.SchedulerProcess;

/**
 * Simple factory that creates StandardProcessExecutionSlip objects on every request for a new
 * slip.
 *
 * Created by art on 6/7/15.
 */
public class StandardProcessExecutionSlipFactory {
  public StandardProcessExecutionSlip createSlip(SchedulerProcess theProcess) {
    return new StandardProcessExecutionSlip(theProcess);
  }
}
