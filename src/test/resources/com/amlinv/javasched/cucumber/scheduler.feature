#  Copyright 2015 AML Innovation & Consulting LLC
#
#  Licensed to the Apache Software Foundation (ASF) under one or more
#  contributor license agreements.  See the NOTICE file distributed with
#  this work for additional information regarding copyright ownership.
#  The ASF licenses this file to You under the Apache License, Version 2.0
#  (the "License"); you may not use this file except in compliance with
#  the License.  You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

Feature: Scheduler

  # Prototype of a scenario for getting started; remove this
  Scenario: Create a scheduler
    Given a scheduler
    Then the scheduler should be created


  Scenario: Verify process steps are executed in order
    Given a scheduler
    When I add a process with 10 steps
    Then I add a process with 5 steps
    Then I add a process with 15 steps
    Then I add a process with 7 steps
    Then the scheduler is started
    When all processes are executed by the scheduler
     And all processes complete within 100 milliseconds
    Then all steps are completed in order

  Scenario: Verify blocking steps do not block non-blocking steps
    Given a scheduler with 1 processing thread and 1 blocking processing thread
      And process "blocking01" with a 100 millisecond blocking step
      And process "nonblocking01" with a 1 millisecond non-blocking step
     Then the scheduler is started
     Then start process "blocking01"
     Then start process "nonblocking01"
     When the process "nonblocking01" completes, process "blocking01" is still running with timeout 1000

  Scenario: Verify blocking steps do not block multiple non-blocking steps
    Given a scheduler with 1 processing thread and 1 blocking processing thread
      And process "blocking01" with a 100 millisecond blocking step
      And process "nonblocking01" with a 1 millisecond non-blocking step
      And process "nonblocking02" with a 1 millisecond non-blocking step
      And process "nonblocking03" with a 1 millisecond non-blocking step
     Then the scheduler is started
     Then start process "blocking01"
     Then start process "nonblocking01"
     Then start process "nonblocking02"
     When the process "nonblocking01" completes, process "blocking01" is still running with timeout 1000
     Then start process "nonblocking03"
     When the process "nonblocking02" completes, process "blocking01" is still running with timeout 1000
     When the process "nonblocking03" completes, process "blocking01" is still running with timeout 1000

  Scenario: Verify slowness on one StepListSchedulerProcess does not affect another
    Given a scheduler with 5 processing threads and 5 blocking processing threads
      And process "blocking01" with a 100 millisecond blocking step
      And process "blocking02" with a 10 millisecond blocking step
     Then the scheduler is started
     Then start process "blocking01"
     Then start process "blocking02"
     When the process "blocking02" completes, process "blocking01" is still running with timeout 1000