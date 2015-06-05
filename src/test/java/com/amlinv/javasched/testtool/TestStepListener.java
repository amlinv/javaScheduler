package com.amlinv.javasched.testtool;

/**
 * Created by art on 12/10/14.
 */
public interface TestStepListener {
  void  stepEntered (int stepNumber);
  void  stepCompleted (int stepNumber);
}
