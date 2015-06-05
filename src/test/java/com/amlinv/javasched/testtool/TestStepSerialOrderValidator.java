package com.amlinv.javasched.testtool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test step listener that validates the order of test steps executed is strictly observed.
 *
 * Created by art on 12/10/14.
 */
public class TestStepSerialOrderValidator implements TestStepListener {
  private static final Logger LOG = LoggerFactory.getLogger(TestStepSerialOrderValidator.class);

  private Object        sync = new Object();

  private boolean       failure = false;
  private int           nextStepEnteredExpected = 0;
  private int           nextStepCompletedExpected = 0;

  /**
   * Return an indication of whether a failure was detected.
   *
   * @return indication of an failure detected.
   */
  public boolean isFailure () {
    return this.failure;
  }

  /**
   * Return the number of the next step that is expected to be completed.
   *
   * @return number of the next step expected to complete.
   */
  public int getNextStepCompletedExpected () {
    return this.nextStepCompletedExpected;
  }

  /**
   * Return the number of the next step that is expected to be started.
   *
   * @return number of the next step expected to start.
   */
  public int getNextStepEnteredExpected () {
    return this.nextStepEnteredExpected;
  }

  /**
   * A step was entered; validate that it's the next one expected.
   *
   * @param stepNumber number of the step that was entered.
   */
  @Override
  public void stepEntered (int stepNumber) {
    synchronized ( this.sync ) {
      if ( stepNumber != this.nextStepEnteredExpected ) {
        LOG.error("incorrect next step entered; have {}, expected {}", stepNumber, this.nextStepEnteredExpected);
        this.failure = true;
      }

      this.nextStepEnteredExpected = stepNumber + 1;
    }
  }

  /**
   * A step was completed; validate that it's the next one expected.
   *
   * @param stepNumber number of the step that was completed.
   */
  @Override
  public void stepCompleted (int stepNumber) {
    synchronized ( this.sync ) {
      if ( stepNumber != this.nextStepCompletedExpected) {
        LOG.error("incorrect next step completed; have {}, expected {}", stepNumber, this.nextStepCompletedExpected);
        this.failure = true;
      }

      this.nextStepCompletedExpected = stepNumber + 1;

      if ( this.nextStepEnteredExpected != this.nextStepCompletedExpected ) {
        LOG.error("apparent simultaneous step execution detected: next-step-entered-expected={}," +
                  " next-step-completed-expected={}",
                  this.nextStepEnteredExpected, this.nextStepCompletedExpected);
        this.failure = true;
      }
    }
  }
}
