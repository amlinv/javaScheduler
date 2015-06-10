/*
 *   Copyright 2015 AML Innovation & Consulting LLC
 *
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.amlinv.javasched;

import java.util.List;

/**
 * Scheduler - the top-level object for running processes.  Processes are submitted to the scheduler
 * which then starts each process by asking for the first step.  Non-blocking steps are immediately
 * queued for execution on a non- blocking thread executor which runs a small number of threads that
 * are intended to be running non-stop except when there are no non-blocking steps ready for
 * execution.
 *
 * Blocking steps, on the other hand, are queued for execution on the blocking-thread executor which
 * will allocate large numbers of threads, as-needed, to sit and wait for blocking operations to
 * process.  Obviously, it's best to use IOC and asynchronous methods to avoid blocking, but that is
 * beyond the scope of the scheduler itself.
 *
 * Created by art on 12/8/14.
 */
public interface Scheduler {

  /**
   * Start execution of the given process.
   *
   * @param newProcess process to execute.
   * @return execution slip that can be used to watch the progress and status of the process.
   */
  SchedulerProcessExecutionSlip startProcess(SchedulerProcess newProcess);

  /**
   * Retrieve the list of processes currently being scheduled.
   *
   * @return list of the scheduled processes.
   */
  List<SchedulerProcess> getProcessList();

  /**
   * Start the scheduler main loop.
   */
  void start();
}
