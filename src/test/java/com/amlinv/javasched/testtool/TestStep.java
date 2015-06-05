package com.amlinv.javasched.testtool;

import com.amlinv.javasched.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test Step that can be used to verify steps are executed serially and in the correct order.  Be
 * sure to use timeouts for the tests.
 *
 * Created by art on 12/10/14.
 */
public class TestStep implements Step {
  private static final Logger  LOG = LoggerFactory.getLogger(TestStep.class);

  private final int         stepNumber;
  private final Object      readySignal;

  private boolean           blocking;
  private boolean           ready;
  private TestStepListener  listener;

  public TestStep (int newStepNumber, boolean startReady) {
    this.blocking    = false;
    this.stepNumber  = newStepNumber;
    this.readySignal = new Object();
    this.ready       = startReady;
  }

  public TestStepListener getListener () {
    return listener;
  }

  public void setListener (TestStepListener newListener) {
    listener = newListener;
  }

  @Override
  public boolean isBlocking () {
    return this.blocking;
  }

  public void setBlocking (boolean newBlocking) {
    this.blocking = newBlocking;
  }

  @Override
  public void execute () {
    // 1 - NOTIFY START
    if ( this.listener != null ) {
      this.listener.stepEntered(this.stepNumber);
    }
    LOG.debug("step started {}", this.stepNumber);

    // 2 - WAIT FOR SIGNAL
    this.waitUntilReady();

    // 3 - NOTIFY COMPLETE
    if ( this.listener != null ) {
      this.listener.stepCompleted(this.stepNumber);
    }
    LOG.debug("step completed {}", this.stepNumber);
  }

  /**
   * Signal this step to start processing; before this happens, the step will block.
   */
  public void signalReady () {
    synchronized ( this.readySignal ) {
      this.ready = true;
      this.readySignal.notifyAll();
    }
  }

  /**
   * Wait until the test signals this step that it is ready to be executed; this is used to ensure order of step
   * execution is correct.
   */
  protected void  waitUntilReady () {
    synchronized ( this.readySignal ) {
      while ( ! this.ready ) {
        try {
          this.readySignal.wait();
        } catch ( InterruptedException intExc ) {
          throw new RuntimeException("test step internal error: interrupted", intExc);
        }
      }
    }
  }
}
