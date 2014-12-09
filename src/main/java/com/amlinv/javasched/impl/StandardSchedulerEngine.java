package com.amlinv.javasched.impl;

import com.amlinv.javasched.SchedulerEngine;

/**
 * Created by art on 12/8/14.
 */
public class StandardSchedulerEngine implements SchedulerEngine {
  private int processorCount = 1;

  @Override
  public int getProcessorCount () {
    return this.processorCount;
  }

  @Override
  public void setProcessorCount (int newNumProcessor) {
    this.processorCount = newNumProcessor;
  }
}
