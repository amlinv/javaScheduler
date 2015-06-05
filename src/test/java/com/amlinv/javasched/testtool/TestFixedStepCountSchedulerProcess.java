package com.amlinv.javasched.testtool;

import com.amlinv.javasched.Step;
import com.amlinv.javasched.impl.StepListSchedulerProcess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Process for testing scheduler operation by creating a fixed number of steps and ensuring they
 * are executed in-order.
 *
 * Created by art on 12/11/14.
 */
public class TestFixedStepCountSchedulerProcess extends StepListSchedulerProcess {

  private static final Logger
      LOG =
      LoggerFactory.getLogger(TestFixedStepCountSchedulerProcess.class);

  private final Object autoContinueSync = new Object();
  private final Object processStateSync = new Object();

  private final TestStep[] mySteps;
  private final TestStepSerialOrderValidator serialOrderValidator;

  private int curStep;
  private boolean autoContinue;
  private int nextAutoStep;

  private boolean finished = false;

  /**
   * Construct a new test process with the given number of steps.
   *
   * @param newNumSteps
   */
  public TestFixedStepCountSchedulerProcess(int newNumSteps) {
    this.serialOrderValidator = new TestStepSerialOrderValidator();

    this.curStep = 0;
    this.mySteps = new TestStep[newNumSteps];

    int iter = 0;
    while (iter < newNumSteps) {
      this.mySteps[iter] = new TestStep(iter, false);
      this.mySteps[iter].setListener(new TestListener());
      iter++;
    }
  }

  /**
   * Return the next step in the process.
   *
   * @return the next step to execute.
   */
  @Override
  public Step getNextStep() {
    if (curStep >= this.mySteps.length) {
      finished = true;
      synchronized (this.processStateSync) {
        this.processStateSync.notifyAll();
      }
      return null;
    }

    Step result = this.mySteps[curStep];
    this.curStep++;

    return result;
  }

  /**
   * Determine if the test process failed.  The order of execution and total number of steps are
   * validated.
   *
   * @return true => if a failure is detected; false => otherwise.
   */
  public boolean isFailure() {
    if (this.serialOrderValidator.isFailure()) {
      return true;
    }

    int nextStepEnteredExpected = this.serialOrderValidator.getNextStepEnteredExpected();
    if (nextStepEnteredExpected != this.mySteps.length) {
      LOG.error(
          "does not appear all steps were executed: next-step-entered-expected={}; number-of-steps={}",
          nextStepEnteredExpected, this.mySteps.length);
      return true;
    }

    int nextStepCompletedExpected = this.serialOrderValidator.getNextStepCompletedExpected();
    if (nextStepCompletedExpected != this.mySteps.length) {
      LOG.error(
          "does not appear all steps were executed: next-step-completed-expected={}; number-of-steps={}",
          nextStepCompletedExpected, this.mySteps.length);
      return true;
    }

    return false;
  }

  /**
   * Start automated, asynchronous, execution of this test process, which means signalling the first
   * step for continuation and then signalling subsequent steps.  The signalling of subsequent steps
   * happens automatically as the completion of each step is detected through a listener on the
   * step leading to a call to onStepCompleted() here.
   */
  public void startAutoExecuteTest() {
    this.autoContinue = true;
    this.nextAutoStep = 1;

    this.mySteps[0].signalReady();
  }

  /**
   * Wait for the process to finish executing.
   *
   * @throws InterruptedException
   */
  public boolean waitUntilFinished(long timeout) throws InterruptedException {
    synchronized (this.processStateSync) {
      if (!this.finished) {
        this.processStateSync.wait(timeout);
      }
    }

    return this.finished;
  }

  /**
   * When a step in the process completes, automatically continue processing, if enabled and the
   * completed step is the last step continued.
   */
  protected void onStepCompleted() {
    synchronized (this.autoContinueSync) {
      if (this.autoContinue) {
        if (this.nextAutoStep < this.mySteps.length) {
          this.mySteps[this.nextAutoStep].signalReady();
          this.nextAutoStep++;
        }
      }
    }
  }

  /**
   * Listener for the completion of steps in order to enable automatic continuation of the test.
   * Also passes on events to the serialOrderValidator.
   */
  protected class TestListener implements TestStepListener {

    @Override
    public void stepEntered(int stepNumber) {
      serialOrderValidator.stepEntered(stepNumber);
    }

    @Override
    public void stepCompleted(int stepNumber) {
      serialOrderValidator.stepCompleted(stepNumber);
      onStepCompleted();
    }
  }

  /**
   * Return a useful description of this object.
   *
   * @return useful description.
   */
  @Override
  public String toString() {
    return "Test scheduler process with " + this.mySteps.length + " steps; id=" +
           System.identityHashCode(this);
  }
}