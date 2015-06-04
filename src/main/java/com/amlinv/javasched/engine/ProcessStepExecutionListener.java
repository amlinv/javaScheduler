package com.amlinv.javasched.engine;

/**
 * Created by art on 6/3/15.
 */
public interface ProcessStepExecutionListener {
  void onStepStarted();
  void onStepStopped();
  void onStepException(Throwable exc);
}
