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
package com.netflix.maestro.engine.utils;

import static org.mockito.Mockito.when;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.maestro.AssertHelper;
import com.netflix.maestro.engine.MaestroEngineBaseTest;
import com.netflix.maestro.engine.execution.StepRuntimeSummary;
import com.netflix.maestro.engine.execution.WorkflowSummary;
import com.netflix.maestro.models.Constants;
import com.netflix.maestro.models.instance.StepInstance;
import com.netflix.maestro.models.instance.StepRuntimeState;
import com.netflix.maestro.models.instance.WorkflowRollupOverview;
import com.netflix.maestro.models.instance.WorkflowRuntimeOverview;
import com.netflix.maestro.models.instance.WorkflowStepStatusSummary;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mock;

public class TaskHelperTest extends MaestroEngineBaseTest {
  @Mock private Task task;
  @Mock private Workflow workflow;

  @Test
  public void testIsUserDefinedTask() {
    when(task.getTaskType()).thenReturn(Constants.MAESTRO_TASK_NAME);
    Assert.assertTrue(TaskHelper.isUserDefinedTask(task));
    when(task.getTaskType()).thenReturn("TEST_TASK");
    Assert.assertFalse(TaskHelper.isUserDefinedTask(task));
  }

  @Test
  public void testIsRealTask() {
    when(task.getTaskType()).thenReturn(Constants.MAESTRO_TASK_NAME);
    when(task.getSeq()).thenReturn(-1);
    Assert.assertFalse(TaskHelper.isRealTask(task));
    when(task.getSeq()).thenReturn(1);
    when(task.getOutputData())
        .thenReturn(
            Collections.singletonMap(
                Constants.STEP_RUNTIME_SUMMARY_FIELD,
                StepRuntimeSummary.builder().runtimeState(new StepRuntimeState()).build()));
    Assert.assertFalse(TaskHelper.isRealTask(task));
    when(task.getOutputData()).thenReturn(Collections.emptyMap());
    Assert.assertTrue(TaskHelper.isRealTask(task));
  }

  @Test
  public void testIsUserDefinedRealTask() {
    when(task.getTaskType()).thenReturn(Constants.MAESTRO_TASK_NAME);
    when(task.getSeq()).thenReturn(1);
    Assert.assertTrue(TaskHelper.isUserDefinedRealTask(task));
    when(task.getTaskType()).thenReturn("TEST_TASK");
    Assert.assertFalse(TaskHelper.isUserDefinedRealTask(task));
    when(task.getTaskType()).thenReturn(Constants.MAESTRO_TASK_NAME);
    when(task.getSeq()).thenReturn(-1);
    Assert.assertFalse(TaskHelper.isUserDefinedRealTask(task));
  }

  @Test
  public void testGetTaskMap() {
    when(workflow.getTasks()).thenReturn(Collections.singletonList(task));
    when(task.getTaskType()).thenReturn(Constants.MAESTRO_TASK_NAME);
    when(task.getReferenceTaskName()).thenReturn("test-job");
    Assert.assertEquals(
        Collections.singletonMap("test-job", task), TaskHelper.getTaskMap(workflow));
  }

  @Test
  public void testGetAllStepOutputData() {
    when(workflow.getTasks()).thenReturn(Collections.singletonList(task));
    when(task.getTaskType()).thenReturn(Constants.MAESTRO_TASK_NAME);
    when(task.getReferenceTaskName()).thenReturn("test-job");
    when(task.getStatus()).thenReturn(Task.Status.COMPLETED);
    when(task.getOutputData()).thenReturn(Collections.singletonMap("foo", "bar"));
    Assert.assertEquals(
        Collections.singletonMap("test-job", Collections.singletonMap("foo", "bar")),
        TaskHelper.getAllStepOutputData(workflow));
  }

  @Test
  public void testGetUserDefinedRealTaskMap() {
    when(workflow.getTasks()).thenReturn(Collections.singletonList(task));
    when(task.getTaskType()).thenReturn(Constants.MAESTRO_TASK_NAME);
    when(task.getReferenceTaskName()).thenReturn("test-job");
    Assert.assertEquals(
        Collections.singletonMap("test-job", task), TaskHelper.getUserDefinedRealTaskMap(workflow));
    when(task.getSeq()).thenReturn(-1);
    Assert.assertEquals(Collections.emptyMap(), TaskHelper.getUserDefinedRealTaskMap(workflow));
  }

  @Test
  public void testComputeOverview() throws Exception {
    WorkflowSummary workflowSummary =
        loadObject("fixtures/parameters/sample-wf-summary-params.json", WorkflowSummary.class);
    Task t = new Task();
    t.setTaskType(Constants.MAESTRO_TASK_NAME);
    t.setSeq(1);
    Map<String, Object> summary = new HashMap<>();
    summary.put("runtime_state", Collections.singletonMap("status", "SUCCEEDED"));
    summary.put("type", "NOOP");
    t.setOutputData(Collections.singletonMap(Constants.STEP_RUNTIME_SUMMARY_FIELD, summary));
    WorkflowRuntimeOverview overview =
        TaskHelper.computeOverview(
            MAPPER,
            workflowSummary,
            new WorkflowRollupOverview(),
            Collections.singletonMap("job1", t));
    Assert.assertEquals(4, overview.getTotalStepCount());
    Assert.assertEquals(
        singletonEnumMap(
            StepInstance.Status.SUCCEEDED,
            WorkflowStepStatusSummary.of(0L).addStep(Arrays.asList(2L, null, null))),
        overview.getStepOverview());
    WorkflowRollupOverview expected = new WorkflowRollupOverview();
    expected.setTotalLeafCount(1L);
    WorkflowRollupOverview.CountReference ref = new WorkflowRollupOverview.CountReference();
    ref.setCnt(1);
    expected.setOverview(singletonEnumMap(StepInstance.Status.SUCCEEDED, ref));
    Assert.assertEquals(expected, overview.getRollupOverview());
  }

  @Test
  public void testToStepStatusMap() throws Exception {
    WorkflowSummary workflowSummary =
        loadObject("fixtures/parameters/sample-wf-summary-params.json", WorkflowSummary.class);
    StepRuntimeState state = new StepRuntimeState();
    state.setStatus(StepInstance.Status.RUNNING);
    state.setStartTime(123L);
    Assert.assertEquals(
        singletonEnumMap(
            StepInstance.Status.RUNNING,
            WorkflowStepStatusSummary.of(0).addStep(Arrays.asList(2L, 123L, null))),
        TaskHelper.toStepStatusMap(workflowSummary, singletonMap("job1", state)));
  }

  @Test
  public void testCheckProgress() throws Exception {
    Task t1 = new Task();
    t1.setTaskType(Constants.MAESTRO_TASK_NAME);
    t1.setSeq(1);
    t1.setReferenceTaskName("job1");
    t1.setStatus(Task.Status.COMPLETED);
    t1.setOutputData(
        Collections.singletonMap(
            Constants.STEP_RUNTIME_SUMMARY_FIELD,
            threeItemMap(
                "runtime_state",
                Collections.singletonMap("status", "SUCCEEDED"),
                "type",
                "NOOP",
                "step_id",
                "job1")));
    Task t2 = new Task();
    t2.setTaskType(Constants.MAESTRO_TASK_NAME);
    t2.setSeq(2);
    t2.setReferenceTaskName("job3");
    t2.setStatus(Task.Status.COMPLETED);
    t2.setOutputData(
        Collections.singletonMap(
            Constants.STEP_RUNTIME_SUMMARY_FIELD,
            threeItemMap(
                "runtime_state",
                Collections.singletonMap("status", "SUCCEEDED"),
                "type",
                "NOOP",
                "step_id",
                "job3")));
    Map<String, Task> realTaskMap = twoItemMap("job1", t1, "job3", t2);

    WorkflowSummary workflowSummary =
        loadObject("fixtures/parameters/sample-wf-summary-params.json", WorkflowSummary.class);

    WorkflowRuntimeOverview overview =
        TaskHelper.computeOverview(
            MAPPER, workflowSummary, new WorkflowRollupOverview(), realTaskMap);

    Optional<Task.Status> actual =
        TaskHelper.checkProgress(realTaskMap, workflowSummary, overview, true);

    Assert.assertFalse(actual.isPresent());

    Task t3 = new Task();
    t3.setTaskType(Constants.MAESTRO_TASK_NAME);
    t3.setSeq(2);
    t3.setReferenceTaskName("job.2");
    t3.setStatus(Task.Status.FAILED);
    t3.setOutputData(
        Collections.singletonMap(
            Constants.STEP_RUNTIME_SUMMARY_FIELD,
            threeItemMap(
                "runtime_state",
                Collections.singletonMap("status", "FATALLY_FAILED"),
                "type",
                "NOOP",
                "step_id",
                "job.2")));
    realTaskMap.put("job.2", t3);

    overview =
        TaskHelper.computeOverview(
            MAPPER, workflowSummary, new WorkflowRollupOverview(), realTaskMap);

    actual = TaskHelper.checkProgress(realTaskMap, workflowSummary, overview, true);
    Assert.assertEquals(Task.Status.FAILED, actual.get());
  }

  @Test
  public void testCheckProgressWithRetry() throws Exception {
    Task t1 = new Task();
    t1.setTaskType(Constants.MAESTRO_TASK_NAME);
    t1.setSeq(1);
    t1.setReferenceTaskName("job1");
    t1.setStatus(Task.Status.COMPLETED);
    t1.setOutputData(
        Collections.singletonMap(
            Constants.STEP_RUNTIME_SUMMARY_FIELD,
            threeItemMap(
                "runtime_state",
                Collections.singletonMap("status", "SUCCEEDED"),
                "type",
                "NOOP",
                "step_id",
                "job1")));
    Task t2 = new Task();
    t2.setTaskType(Constants.MAESTRO_TASK_NAME);
    t2.setSeq(2);
    t2.setReferenceTaskName("job3");
    t2.setStatus(Task.Status.SCHEDULED);
    t2.setOutputData(
        Collections.singletonMap(
            Constants.STEP_RUNTIME_SUMMARY_FIELD,
            threeItemMap(
                "runtime_state",
                Collections.singletonMap("status", "USER_FAILED"),
                "type",
                "NOOP",
                "step_id",
                "job3")));
    Map<String, Task> realTaskMap = twoItemMap("job1", t1, "job3", t2);

    Task t3 = new Task();
    t3.setTaskType(Constants.MAESTRO_TASK_NAME);
    t3.setSeq(2);
    t3.setReferenceTaskName("job.2");
    t3.setStatus(Task.Status.FAILED);
    t3.setOutputData(
        Collections.singletonMap(
            Constants.STEP_RUNTIME_SUMMARY_FIELD,
            threeItemMap(
                "runtime_state",
                Collections.singletonMap("status", "FATALLY_FAILED"),
                "type",
                "NOOP",
                "step_id",
                "job.2")));
    realTaskMap.put("job.2", t3);

    WorkflowSummary workflowSummary =
        loadObject("fixtures/parameters/sample-wf-summary-params.json", WorkflowSummary.class);

    WorkflowRuntimeOverview overview =
        TaskHelper.computeOverview(
            MAPPER, workflowSummary, new WorkflowRollupOverview(), realTaskMap);

    Optional<Task.Status> actual =
        TaskHelper.checkProgress(realTaskMap, workflowSummary, overview, true);
    Assert.assertFalse(actual.isPresent());

    t2.setStatus(Task.Status.CANCELED);
    actual = TaskHelper.checkProgress(realTaskMap, workflowSummary, overview, true);
    Assert.assertEquals(Task.Status.FAILED, actual.get());
  }

  @Test
  public void testCheckProgressForRestart() throws Exception {
    Task t1 = new Task();
    t1.setTaskType(Constants.MAESTRO_TASK_NAME);
    t1.setSeq(1);
    t1.setReferenceTaskName("job3");
    t1.setStatus(Task.Status.COMPLETED);
    t1.setOutputData(
        Collections.singletonMap(
            Constants.STEP_RUNTIME_SUMMARY_FIELD,
            twoItemMap(
                "runtime_state", Collections.singletonMap("status", "SUCCEEDED"), "type", "NOOP")));
    Map<String, Task> realTaskMap = Collections.singletonMap("job3", t1);

    WorkflowSummary workflowSummary =
        loadObject(
            "fixtures/parameters/sample-wf-summary-restart-config.json", WorkflowSummary.class);

    WorkflowRuntimeOverview overview =
        TaskHelper.computeOverview(
            MAPPER, workflowSummary, new WorkflowRollupOverview(), realTaskMap);

    Optional<Task.Status> actual =
        TaskHelper.checkProgress(realTaskMap, workflowSummary, overview, true);
    Assert.assertEquals(Task.Status.FAILED_WITH_TERMINAL_ERROR, actual.get());

    t1.setReferenceTaskName("job.2");
    overview =
        TaskHelper.computeOverview(
            MAPPER, workflowSummary, new WorkflowRollupOverview(), realTaskMap);

    actual = TaskHelper.checkProgress(realTaskMap, workflowSummary, overview, true);
    Assert.assertFalse(actual.isPresent());
  }

  @Test
  public void testCheckProgressInvalid() throws Exception {
    Task t1 = new Task();
    t1.setTaskType(Constants.MAESTRO_TASK_NAME);
    t1.setSeq(1);
    t1.setReferenceTaskName("job4");
    t1.setStatus(Task.Status.COMPLETED);
    t1.setOutputData(
        Collections.singletonMap(
            Constants.STEP_RUNTIME_SUMMARY_FIELD,
            twoItemMap(
                "runtime_state", Collections.singletonMap("status", "SUCCEEDED"), "type", "NOOP")));
    Map<String, Task> realTaskMap = Collections.singletonMap("job4", t1);

    WorkflowSummary workflowSummary =
        loadObject(
            "fixtures/parameters/sample-wf-summary-restart-config.json", WorkflowSummary.class);

    WorkflowRuntimeOverview overview =
        TaskHelper.computeOverview(
            MAPPER, workflowSummary, new WorkflowRollupOverview(), realTaskMap);

    AssertHelper.assertThrows(
        "Invalid status for steps",
        IllegalArgumentException.class,
        "Invalid state: stepId [job4] should not have any status",
        () -> TaskHelper.checkProgress(realTaskMap, workflowSummary, overview, true));
  }

  @Test
  public void testCheckProgressWithEmptyDag() {
    Optional<Task.Status> actual =
        TaskHelper.checkProgress(
            Collections.emptyMap(), new WorkflowSummary(), new WorkflowRuntimeOverview(), true);
    Assert.assertEquals(Task.Status.FAILED, actual.get());
  }

  @Test
  public void testCheckProgressWhileNotFinal() throws Exception {
    Task t1 = new Task();
    t1.setTaskType(Constants.MAESTRO_TASK_NAME);
    t1.setSeq(1);
    t1.setReferenceTaskName("job1");
    t1.setStatus(Task.Status.COMPLETED);
    t1.setOutputData(
        Collections.singletonMap(
            Constants.STEP_RUNTIME_SUMMARY_FIELD,
            threeItemMap(
                "runtime_state",
                Collections.singletonMap("status", "SUCCEEDED"),
                "type",
                "NOOP",
                "step_id",
                "job1")));
    Task t2 = new Task();
    t2.setTaskType(Constants.MAESTRO_TASK_NAME);
    t2.setSeq(2);
    t2.setReferenceTaskName("job3");
    t2.setStatus(Task.Status.FAILED);
    t2.setOutputData(
        Collections.singletonMap(
            Constants.STEP_RUNTIME_SUMMARY_FIELD,
            threeItemMap(
                "runtime_state",
                Collections.singletonMap("status", "USER_FAILED"),
                "type",
                "NOOP",
                "step_id",
                "job3")));
    Map<String, Task> realTaskMap = twoItemMap("job1", t1, "job3", t2);

    Task t3 = new Task();
    t3.setTaskType(Constants.MAESTRO_TASK_NAME);
    t3.setSeq(2);
    t3.setReferenceTaskName("job.2");
    t3.setStatus(Task.Status.FAILED);
    t3.setOutputData(
        Collections.singletonMap(
            Constants.STEP_RUNTIME_SUMMARY_FIELD,
            threeItemMap(
                "runtime_state",
                Collections.singletonMap("status", "FATALLY_FAILED"),
                "type",
                "NOOP",
                "step_id",
                "job.2")));
    realTaskMap.put("job.2", t3);

    WorkflowSummary workflowSummary =
        loadObject("fixtures/parameters/sample-wf-summary-params.json", WorkflowSummary.class);

    WorkflowRuntimeOverview overview =
        TaskHelper.computeOverview(
            MAPPER, workflowSummary, new WorkflowRollupOverview(), realTaskMap);

    Optional<Task.Status> actual =
        TaskHelper.checkProgress(realTaskMap, workflowSummary, overview, true);
    Assert.assertEquals(Task.Status.FAILED, actual.get());

    actual = TaskHelper.checkProgress(realTaskMap, workflowSummary, overview, false);
    Assert.assertFalse(actual.isPresent());
  }
}
