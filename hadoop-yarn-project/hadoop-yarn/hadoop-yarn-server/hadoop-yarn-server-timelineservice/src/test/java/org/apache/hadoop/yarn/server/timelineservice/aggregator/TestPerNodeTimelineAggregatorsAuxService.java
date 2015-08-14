/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.server.timelineservice.aggregator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.ExitUtil;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.server.api.ContainerInitializationContext;
import org.apache.hadoop.yarn.server.api.ContainerTerminationContext;
import org.junit.Test;

public class TestPerNodeTimelineAggregatorsAuxService {
  private ApplicationAttemptId appAttemptId;

  public TestPerNodeTimelineAggregatorsAuxService() {
    ApplicationId appId =
        ApplicationId.newInstance(System.currentTimeMillis(), 1);
    appAttemptId = ApplicationAttemptId.newInstance(appId, 1);
  }

  @Test
  public void testAddApplication() throws Exception {
    PerNodeTimelineAggregatorsAuxService auxService = createAggregatorAndAddApplication();
    // auxService should have a single app
    assertTrue(auxService.hasApplication(
        appAttemptId.getApplicationId().toString()));
    auxService.close();
  }

  @Test
  public void testAddApplicationNonAMContainer() throws Exception {
    PerNodeTimelineAggregatorsAuxService auxService = createAggregator();

    ContainerId containerId = getContainerId(2L); // not an AM
    ContainerInitializationContext context =
        mock(ContainerInitializationContext.class);
    when(context.getContainerId()).thenReturn(containerId);
    auxService.initializeContainer(context);
    // auxService should not have that app
    assertFalse(auxService.hasApplication(
        appAttemptId.getApplicationId().toString()));
  }

  @Test
  public void testRemoveApplication() throws Exception {
    PerNodeTimelineAggregatorsAuxService auxService = createAggregatorAndAddApplication();
    // auxService should have a single app
    String appIdStr = appAttemptId.getApplicationId().toString();
    assertTrue(auxService.hasApplication(appIdStr));

    ContainerId containerId = getAMContainerId();
    ContainerTerminationContext context =
        mock(ContainerTerminationContext.class);
    when(context.getContainerId()).thenReturn(containerId);
    auxService.stopContainer(context);
    // auxService should not have that app
    assertFalse(auxService.hasApplication(appIdStr));
    auxService.close();
  }

  @Test
  public void testRemoveApplicationNonAMContainer() throws Exception {
    PerNodeTimelineAggregatorsAuxService auxService = createAggregatorAndAddApplication();
    // auxService should have a single app
    String appIdStr = appAttemptId.getApplicationId().toString();
    assertTrue(auxService.hasApplication(appIdStr));

    ContainerId containerId = getContainerId(2L); // not an AM
    ContainerTerminationContext context =
        mock(ContainerTerminationContext.class);
    when(context.getContainerId()).thenReturn(containerId);
    auxService.stopContainer(context);
    // auxService should still have that app
    assertTrue(auxService.hasApplication(appIdStr));
    auxService.close();
  }

  @Test(timeout = 60000)
  public void testLaunch() throws Exception {
    ExitUtil.disableSystemExit();
    PerNodeTimelineAggregatorsAuxService auxService = null;
    try {
      auxService =
          PerNodeTimelineAggregatorsAuxService.launchServer(new String[0]);
    } catch (ExitUtil.ExitException e) {
      assertEquals(0, e.status);
      ExitUtil.resetFirstExitException();
      fail();
    } finally {
      if (auxService != null) {
        auxService.stop();
      }
    }
  }

  private PerNodeTimelineAggregatorsAuxService createAggregatorAndAddApplication() {
    PerNodeTimelineAggregatorsAuxService auxService = createAggregator();
    // create an AM container
    ContainerId containerId = getAMContainerId();
    ContainerInitializationContext context =
        mock(ContainerInitializationContext.class);
    when(context.getContainerId()).thenReturn(containerId);
    auxService.initializeContainer(context);
    return auxService;
  }

  private PerNodeTimelineAggregatorsAuxService createAggregator() {
    TimelineAggregatorsCollection
        aggregatorsCollection = spy(new TimelineAggregatorsCollection());
    doReturn(new Configuration()).when(aggregatorsCollection).getConfig();
    PerNodeTimelineAggregatorsAuxService auxService =
        spy(new PerNodeTimelineAggregatorsAuxService(aggregatorsCollection));
    return auxService;
  }

  private ContainerId getAMContainerId() {
    return getContainerId(1L);
  }

  private ContainerId getContainerId(long id) {
    return ContainerId.newContainerId(appAttemptId, id);
  }
}
