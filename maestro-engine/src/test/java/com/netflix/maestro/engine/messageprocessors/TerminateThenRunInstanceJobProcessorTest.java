/*
 * Copyright 2024 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.maestro.engine.messageprocessors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.maestro.AssertHelper;
import com.netflix.maestro.engine.MaestroEngineBaseTest;
import com.netflix.maestro.engine.dao.MaestroStepInstanceActionDao;
import com.netflix.maestro.engine.dao.MaestroWorkflowInstanceDao;
import com.netflix.maestro.engine.db.InstanceRunUuid;
import com.netflix.maestro.engine.jobevents.RunWorkflowInstancesJobEvent;
import com.netflix.maestro.engine.jobevents.TerminateThenRunInstanceJobEvent;
import com.netflix.maestro.engine.processors.TerminateInstancesJobProcessor;
import com.netflix.maestro.engine.processors.TerminateThenRunInstanceJobProcessor;
import com.netflix.maestro.engine.publisher.MaestroJobEventPublisher;
import com.netflix.maestro.exceptions.MaestroInternalError;
import com.netflix.maestro.exceptions.MaestroNotFoundException;
import com.netflix.maestro.exceptions.MaestroRetryableError;
import com.netflix.maestro.models.Actions;
import com.netflix.maestro.models.definition.User;
import com.netflix.maestro.models.instance.WorkflowInstance;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class TerminateThenRunInstanceJobProcessorTest extends MaestroEngineBaseTest {
  @Mock private MaestroJobEventPublisher publisher;
  @Mock private MaestroWorkflowInstanceDao instanceDao;
  @Mock private MaestroStepInstanceActionDao actionDao;
  @Mock private WorkflowInstance instance1;
  @Mock private WorkflowInstance instance2;
  @Mock private WorkflowInstance instance3;

  private final String workflowId = "sample-minimal-wf";
  private final User tester = User.create("tester");
  private TerminateThenRunInstanceJobProcessor processor;
  private TerminateThenRunInstanceJobEvent jobEvent1;
  private TerminateThenRunInstanceJobEvent jobEvent2;

  @Before
  public void before() throws Exception {
    TerminateInstancesJobProcessor terminateInstancesJobProcessor =
        new TerminateInstancesJobProcessor(instanceDao, actionDao);
    processor =
        new TerminateThenRunInstanceJobProcessor(
            publisher, instanceDao, terminateInstancesJobProcessor);
    jobEvent1 =
        TerminateThenRunInstanceJobEvent.init(
            workflowId, Actions.WorkflowInstanceAction.STOP, tester, "test-reason");
    jobEvent1.addOneRun(new InstanceRunUuid(1L, 1L, "uuid1"));
    jobEvent1.addOneRun(new InstanceRunUuid(2L, 1L, "uuid2"));
    jobEvent1.addRunAfter(3L, 1L, "uuid3");
    jobEvent2 =
        TerminateThenRunInstanceJobEvent.init(
            workflowId, Actions.WorkflowInstanceAction.KILL, tester, "test-reason");
    jobEvent2.addOneRun(new InstanceRunUuid(1L, 1L, "uuid1"));
    jobEvent2.addOneRun(new InstanceRunUuid(2L, 1L, "uuid2"));
    jobEvent2.addOneRun(new InstanceRunUuid(3L, 1L, "uuid3"));
    when(instanceDao.getWorkflowInstanceRun(workflowId, 1L, 1L)).thenReturn(instance1);
    when(instanceDao.getWorkflowInstanceRun(workflowId, 2L, 1L)).thenReturn(instance2);
    when(instanceDao.getWorkflowInstanceRun(workflowId, 3L, 1L)).thenReturn(instance3);
    when(instance1.getWorkflowId()).thenReturn(workflowId);
    when(instance1.getWorkflowInstanceId()).thenReturn(1L);
    when(instance1.getWorkflowRunId()).thenReturn(1L);
    when(instance2.getWorkflowId()).thenReturn(workflowId);
    when(instance2.getWorkflowInstanceId()).thenReturn(2L);
    when(instance2.getWorkflowRunId()).thenReturn(1L);
    when(instance3.getWorkflowId()).thenReturn(workflowId);
    when(instance3.getWorkflowInstanceId()).thenReturn(3L);
    when(instance3.getWorkflowRunId()).thenReturn(1L);
  }

  @Test
  public void testTerminateWorkflowInstancesWithRunAfterNotDone() {
    when(instance1.getStatus()).thenReturn(WorkflowInstance.Status.CREATED);
    when(instance1.getWorkflowUuid()).thenReturn("uuid1");
    when(instance1.getExecutionId()).thenReturn("exe1");
    when(instance2.getStatus()).thenReturn(WorkflowInstance.Status.CREATED);
    when(instance2.getWorkflowUuid()).thenReturn("uuid2");
    when(instance2.getExecutionId()).thenReturn("exe2");
    when(instanceDao.getWorkflowInstanceStatus(workflowId, 1L, 1L)).thenReturn(null);
    when(instanceDao.getWorkflowInstanceStatus(workflowId, 2L, 1L))
        .thenReturn(WorkflowInstance.Status.STOPPED);

    AssertHelper.assertThrows(
        "throw MaestroRetryableError to retry",
        MaestroRetryableError.class,
        "[InstanceRunUuid(instanceId=1, runId=1, uuid=uuid1)] is still terminating and will check it again",
        () -> processor.process(() -> jobEvent1));

    verify(actionDao, times(2)).terminate(any(), any(), any(), anyString());
    verify(actionDao, times(1))
        .terminate(instance1, tester, Actions.WorkflowInstanceAction.STOP, "test-reason");
    verify(publisher, times(0)).publishOrThrow(any(RunWorkflowInstancesJobEvent.class), any());
  }

  @Test
  public void testTerminateWorkflowInstancesWithRunAfterDone() {
    when(instance1.getStatus()).thenReturn(WorkflowInstance.Status.CREATED);
    when(instance1.getWorkflowUuid()).thenReturn("uuid1");
    when(instance1.getExecutionId()).thenReturn("exe1");
    when(instance2.getStatus()).thenReturn(WorkflowInstance.Status.CREATED);
    when(instance2.getWorkflowUuid()).thenReturn("uuid2");
    when(instance2.getExecutionId()).thenReturn("exe2");
    when(instanceDao.getWorkflowInstanceStatus(workflowId, 1L, 1L))
        .thenReturn(WorkflowInstance.Status.FAILED);
    when(instanceDao.getWorkflowInstanceStatus(workflowId, 2L, 1L))
        .thenReturn(WorkflowInstance.Status.STOPPED);
    processor.process(() -> jobEvent1);
    verify(actionDao, times(2)).terminate(any(), any(), any(), anyString());
    verify(actionDao, times(1))
        .terminate(instance1, tester, Actions.WorkflowInstanceAction.STOP, "test-reason");
    verify(actionDao, times(1))
        .terminate(instance2, tester, Actions.WorkflowInstanceAction.STOP, "test-reason");
    verify(publisher, times(1)).publishOrThrow(any(RunWorkflowInstancesJobEvent.class), any());
  }

  @Test
  public void testTerminateWorkflowInstancesWithoutRunAfterNotDone() {
    when(instance1.getStatus()).thenReturn(WorkflowInstance.Status.CREATED);
    when(instance1.getWorkflowUuid()).thenReturn("uuid1");
    when(instance1.getExecutionId()).thenReturn("exe1");
    when(instance2.getStatus()).thenReturn(WorkflowInstance.Status.CREATED);
    when(instance2.getWorkflowUuid()).thenReturn("uuid2");
    when(instance2.getExecutionId()).thenReturn("exe2");
    when(instance3.getStatus()).thenReturn(WorkflowInstance.Status.CREATED);
    when(instance3.getWorkflowUuid()).thenReturn("uuid3");
    when(instance3.getExecutionId()).thenReturn("exe3");
    when(instanceDao.getWorkflowInstanceStatus(workflowId, 1L, 1L)).thenReturn(null);
    when(instanceDao.getWorkflowInstanceStatus(workflowId, 2L, 1L))
        .thenReturn(WorkflowInstance.Status.STOPPED);
    when(instanceDao.getWorkflowInstanceStatus(workflowId, 3L, 1L))
        .thenReturn(WorkflowInstance.Status.CREATED);

    AssertHelper.assertThrows(
        "throw MaestroRetryableError to retry",
        MaestroRetryableError.class,
        "[InstanceRunUuid(instanceId=1, runId=1, uuid=uuid1)] is still terminating and will check it again",
        () -> processor.process(() -> jobEvent2));

    verify(actionDao, times(3)).terminate(any(), any(), any(), anyString());
    verify(actionDao, times(1))
        .terminate(instance1, tester, Actions.WorkflowInstanceAction.KILL, "test-reason");
    verify(actionDao, times(1))
        .terminate(instance2, tester, Actions.WorkflowInstanceAction.KILL, "test-reason");
    verify(actionDao, times(1))
        .terminate(instance3, tester, Actions.WorkflowInstanceAction.KILL, "test-reason");
    verify(publisher, times(0)).publishOrThrow(any(RunWorkflowInstancesJobEvent.class), any());
  }

  @Test
  public void testTerminateWorkflowInstancesWithoutRunAfterDone() {
    when(instance1.getStatus()).thenReturn(WorkflowInstance.Status.SUCCEEDED);
    when(instance1.getWorkflowUuid()).thenReturn("uuid1");
    when(instance2.getStatus()).thenReturn(WorkflowInstance.Status.CREATED);
    when(instance2.getWorkflowUuid()).thenReturn("uuid2");
    when(instance2.getExecutionId()).thenReturn("exe2");
    when(instance3.getStatus()).thenReturn(WorkflowInstance.Status.CREATED);
    when(instance3.getWorkflowUuid()).thenReturn("uuid3");
    when(instance3.getExecutionId()).thenReturn("exe3");
    when(instanceDao.getWorkflowInstanceStatus(workflowId, 2L, 1L))
        .thenReturn(WorkflowInstance.Status.STOPPED);
    when(instanceDao.getWorkflowInstanceStatus(workflowId, 3L, 1L))
        .thenReturn(WorkflowInstance.Status.FAILED);
    processor.process(() -> jobEvent2);
    verify(actionDao, times(2)).terminate(any(), any(), any(), anyString());
    verify(actionDao, times(1))
        .terminate(instance2, tester, Actions.WorkflowInstanceAction.KILL, "test-reason");
    verify(actionDao, times(1))
        .terminate(instance3, tester, Actions.WorkflowInstanceAction.KILL, "test-reason");
    verify(publisher, times(0)).publishOrThrow(any(RunWorkflowInstancesJobEvent.class), any());
  }

  @Test
  public void testNotTerminateWorkflowInstances() {
    when(instance1.getStatus()).thenReturn(WorkflowInstance.Status.SUCCEEDED);
    when(instance1.getWorkflowUuid()).thenReturn("uuid1");
    when(instance2.getStatus()).thenReturn(WorkflowInstance.Status.FAILED);
    when(instance2.getWorkflowUuid()).thenReturn("uuid2");
    when(instanceDao.getWorkflowInstanceStatus(workflowId, 2L, 1L))
        .thenReturn(WorkflowInstance.Status.STOPPED);
    when(instanceDao.getWorkflowInstanceStatus(workflowId, 3L, 1L))
        .thenReturn(WorkflowInstance.Status.FAILED);
    processor.process(() -> jobEvent1);
    verify(actionDao, times(0)).terminate(any(), any(), any(), anyString());
    verify(publisher, times(1)).publishOrThrow(any(RunWorkflowInstancesJobEvent.class), any());
    reset(publisher);

    when(instance3.getStatus()).thenReturn(WorkflowInstance.Status.STOPPED);
    when(instance3.getWorkflowUuid()).thenReturn("uuid3");
    processor.process(() -> jobEvent2);
    verify(actionDao, times(0)).terminate(any(), any(), any(), anyString());
    verify(publisher, times(0)).publishOrThrow(any(RunWorkflowInstancesJobEvent.class), any());
  }

  @Test
  public void testTerminateWorkflowInstancesUuidNotMatchException() {
    when(instance1.getStatus()).thenReturn(WorkflowInstance.Status.CREATED);
    when(instance1.getExecutionId()).thenReturn("exe1");
    when(instance1.getWorkflowUuid()).thenReturn("uuid2");
    AssertHelper.assertThrows(
        "Instance uuid mismatch",
        MaestroInternalError.class,
        "in job event does not match DB row uuid [uuid2]",
        () -> processor.process(() -> jobEvent1));
  }

  @Test
  public void testTerminateWorkflowInstancesMaestroNotFoundException() {
    when(instance1.getStatus()).thenReturn(WorkflowInstance.Status.CREATED);
    when(instance1.getWorkflowUuid()).thenReturn("uuid1");
    when(instance1.getExecutionId()).thenReturn("exe1");
    when(instance2.getStatus()).thenThrow(new MaestroNotFoundException("test"));
    when(instanceDao.getWorkflowInstanceStatus(workflowId, 1L, 1L))
        .thenReturn(WorkflowInstance.Status.STOPPED);

    processor.process(() -> jobEvent1);
    verify(actionDao, times(1)).terminate(any(), any(), any(), anyString());
    verify(actionDao, times(1))
        .terminate(instance1, tester, Actions.WorkflowInstanceAction.STOP, "test-reason");
    verify(publisher, times(1)).publishOrThrow(any(RunWorkflowInstancesJobEvent.class), any());
  }

  @Test
  public void testTerminateWorkflowInstancesNullPointerException() {
    when(instance1.getStatus()).thenReturn(WorkflowInstance.Status.CREATED);
    when(instance1.getWorkflowUuid()).thenReturn("uuid1");
    when(instance1.getExecutionId()).thenReturn(null);
    AssertHelper.assertThrows(
        "Something is null",
        MaestroInternalError.class,
        "Something is null",
        () -> processor.process(() -> jobEvent1));

    when(instance1.getStatus()).thenThrow(new NullPointerException("test"));
    AssertHelper.assertThrows(
        "Something is null",
        MaestroInternalError.class,
        "Something is null",
        () -> processor.process(() -> jobEvent1));
  }

  @Test
  public void testTerminateWorkflowInstancesRuntimeException() {
    when(instance1.getStatus()).thenThrow(new RuntimeException("test"));
    AssertHelper.assertThrows(
        "Failed to run",
        MaestroRetryableError.class,
        "Failed to terminate a workflow and will retry to terminate it.",
        () -> processor.process(() -> jobEvent1));
  }
}
