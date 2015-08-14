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

package org.apache.hadoop.yarn.server.timelineservice.collector;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.server.api.CollectorNodemanagerProtocol;
import org.junit.Test;

public class TestTimelineCollectorManager {

  @Test(timeout=60000)
  public void testMultithreadedAdd() throws Exception {
    final TimelineCollectorManager collectorManager = createCollectorManager();

    final int NUM_APPS = 5;
    List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
    for (int i = 0; i < NUM_APPS; i++) {
      final ApplicationId appId = ApplicationId.newInstance(0L, i);
      Callable<Boolean> task = new Callable<Boolean>() {
        public Boolean call() {
          AppLevelTimelineCollector collector =
              new AppLevelTimelineCollector(appId.toString());
          return (collectorManager.putIfAbsent(appId, collector) == collector);
        }
      };
      tasks.add(task);
    }
    ExecutorService executor = Executors.newFixedThreadPool(NUM_APPS);
    try {
      List<Future<Boolean>> futures = executor.invokeAll(tasks);
      for (Future<Boolean> future: futures) {
        assertTrue(future.get());
      }
    } finally {
      executor.shutdownNow();
    }
    // check the keys
    for (int i = 0; i < NUM_APPS; i++) {
      final ApplicationId appId = ApplicationId.newInstance(0L, i);
      assertTrue(collectorManager.containsKey(appId.toString()));
    }
  }

  @Test
  public void testMultithreadedAddAndRemove() throws Exception {
    final TimelineCollectorManager collectorManager = createCollectorManager();

    final int NUM_APPS = 5;
    List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
    for (int i = 0; i < NUM_APPS; i++) {
      final ApplicationId appId = ApplicationId.newInstance(0L, i);
      Callable<Boolean> task = new Callable<Boolean>() {
        public Boolean call() {
          AppLevelTimelineCollector collector =
              new AppLevelTimelineCollector(appId.toString());
          boolean successPut =
              (collectorManager.putIfAbsent(appId, collector) == collector);
          return successPut && collectorManager.remove(appId.toString());
        }
      };
      tasks.add(task);
    }
    ExecutorService executor = Executors.newFixedThreadPool(NUM_APPS);
    try {
      List<Future<Boolean>> futures = executor.invokeAll(tasks);
      for (Future<Boolean> future: futures) {
        assertTrue(future.get());
      }
    } finally {
      executor.shutdownNow();
    }
    // check the keys
    for (int i = 0; i < NUM_APPS; i++) {
      final ApplicationId appId = ApplicationId.newInstance(0L, i);
      assertFalse(collectorManager.containsKey(appId.toString()));
    }
  }

  private TimelineCollectorManager createCollectorManager() {
    final TimelineCollectorManager collectorManager =
        spy(new TimelineCollectorManager());
    doReturn(new Configuration()).when(collectorManager).getConfig();
    CollectorNodemanagerProtocol nmCollectorService =
        mock(CollectorNodemanagerProtocol.class);
    doReturn(nmCollectorService).when(collectorManager).getNMCollectorService();
    return collectorManager;
  }
}
