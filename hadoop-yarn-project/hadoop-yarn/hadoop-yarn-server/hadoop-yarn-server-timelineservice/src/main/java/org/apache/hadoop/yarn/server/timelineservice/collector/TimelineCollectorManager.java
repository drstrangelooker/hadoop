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

import static org.apache.hadoop.fs.CommonConfigurationKeys.DEFAULT_HADOOP_HTTP_STATIC_USER;
import static org.apache.hadoop.fs.CommonConfigurationKeys.HADOOP_HTTP_STATIC_USER;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience.Private;
import org.apache.hadoop.classification.InterfaceStability.Unstable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.http.HttpServer2;
import org.apache.hadoop.http.lib.StaticUserWebFilter;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.service.CompositeService;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.exceptions.YarnRuntimeException;
import org.apache.hadoop.yarn.ipc.YarnRPC;
import org.apache.hadoop.yarn.server.api.CollectorNodemanagerProtocol;
import org.apache.hadoop.yarn.server.api.protocolrecords.ReportNewCollectorInfoRequest;
import org.apache.hadoop.yarn.webapp.GenericExceptionHandler;
import org.apache.hadoop.yarn.webapp.YarnJacksonJaxbJsonProvider;
import org.apache.hadoop.yarn.webapp.util.WebAppUtils;

import com.google.common.annotations.VisibleForTesting;

/**
 * Class that manages adding and removing collectors and their lifecycle. It
 * provides thread safety access to the collectors inside.
 *
 * It is a singleton, and instances should be obtained via
 * {@link #getInstance()}.
 */
@Private
@Unstable
public class TimelineCollectorManager extends CompositeService {
  private static final Log LOG =
      LogFactory.getLog(TimelineCollectorManager.class);
  private static final TimelineCollectorManager INSTANCE =
      new TimelineCollectorManager();

  // access to this map is synchronized with the map itself
  private final Map<String, TimelineCollector> collectors =
      Collections.synchronizedMap(
          new HashMap<String, TimelineCollector>());

  // REST server for this collector manager
  private HttpServer2 timelineRestServer;

  private String timelineRestServerBindAddress;

  private CollectorNodemanagerProtocol nmCollectorService;

  private InetSocketAddress nmCollectorServiceAddress;

  static final String COLLECTOR_MANAGER_ATTR_KEY = "collector.manager";

  static TimelineCollectorManager getInstance() {
    return INSTANCE;
  }

  @VisibleForTesting
  protected TimelineCollectorManager() {
    super(TimelineCollectorManager.class.getName());
  }

  @Override
  public void serviceInit(Configuration conf) throws Exception {
    this.nmCollectorServiceAddress = conf.getSocketAddr(
        YarnConfiguration.NM_BIND_HOST,
        YarnConfiguration.NM_COLLECTOR_SERVICE_ADDRESS,
        YarnConfiguration.DEFAULT_NM_COLLECTOR_SERVICE_ADDRESS,
        YarnConfiguration.DEFAULT_NM_COLLECTOR_SERVICE_PORT);

  }

  @Override
  protected void serviceStart() throws Exception {
    startWebApp();
    super.serviceStart();
  }

  @Override
  protected void serviceStop() throws Exception {
    if (timelineRestServer != null) {
      timelineRestServer.stop();
    }
    super.serviceStop();
  }

  /**
   * Put the collector into the collection if an collector mapped by id does
   * not exist.
   *
   * @throws YarnRuntimeException if there was any exception in initializing and
   * starting the app level service
   * @return the collector associated with id after the potential put.
   */
  public TimelineCollector putIfAbsent(ApplicationId appId,
      TimelineCollector collector) {
    String id = appId.toString();
    TimelineCollector collectorInTable;
    boolean collectorIsNew = false;
    synchronized (collectors) {
      collectorInTable = collectors.get(id);
      if (collectorInTable == null) {
        try {
          // initialize, start, and add it to the collection so it can be
          // cleaned up when the parent shuts down
          collector.init(getConfig());
          collector.start();
          collectors.put(id, collector);
          LOG.info("the collector for " + id + " was added");
          collectorInTable = collector;
          collectorIsNew = true;
        } catch (Exception e) {
          throw new YarnRuntimeException(e);
        }
      } else {
        String msg = "the collector for " + id + " already exists!";
        LOG.error(msg);
      }

    }
    // Report to NM if a new collector is added.
    if (collectorIsNew) {
      try {
        reportNewCollectorToNM(appId);
      } catch (Exception e) {
        // throw exception here as it cannot be used if failed report to NM
        LOG.error("Failed to report a new collector for application: " + appId +
            " to the NM Collector Service.");
        throw new YarnRuntimeException(e);
      }
    }

    return collectorInTable;
  }

  /**
   * Removes the collector for the specified id. The collector is also stopped
   * as a result. If the collector does not exist, no change is made.
   *
   * @return whether it was removed successfully
   */
  public boolean remove(String id) {
    synchronized (collectors) {
      TimelineCollector collector = collectors.remove(id);
      if (collector == null) {
        String msg = "the collector for " + id + " does not exist!";
        LOG.error(msg);
        return false;
      } else {
        // stop the service to do clean up
        collector.stop();
        LOG.info("the collector service for " + id + " was removed");
        return true;
      }
    }
  }

  /**
   * Returns the collector for the specified id.
   *
   * @return the collector or null if it does not exist
   */
  public TimelineCollector get(String id) {
    return collectors.get(id);
  }

  /**
   * Returns whether the collector for the specified id exists in this
   * collection.
   */
  public boolean containsKey(String id) {
    return collectors.containsKey(id);
  }

  /**
   * Launch the REST web server for this collector manager
   */
  private void startWebApp() {
    Configuration conf = getConfig();
    // use the same ports as the old ATS for now; we could create new properties
    // for the new timeline service if needed
    String bindAddress = WebAppUtils.getWebAppBindURL(conf,
        YarnConfiguration.TIMELINE_SERVICE_BIND_HOST,
        WebAppUtils.getAHSWebAppURLWithoutScheme(conf));
    this.timelineRestServerBindAddress = WebAppUtils.getResolvedAddress(
        NetUtils.createSocketAddr(bindAddress));
    LOG.info("Instantiating the per-node collector webapp at " +
        timelineRestServerBindAddress);
    try {
      Configuration confForInfoServer = new Configuration(conf);
      confForInfoServer.setInt(HttpServer2.HTTP_MAX_THREADS, 10);
      HttpServer2.Builder builder = new HttpServer2.Builder()
          .setName("timeline")
          .setConf(conf)
          .addEndpoint(URI.create("http://" + bindAddress));
      timelineRestServer = builder.build();
      // TODO: replace this by an authentication filter in future.
      HashMap<String, String> options = new HashMap<>();
      String username = conf.get(HADOOP_HTTP_STATIC_USER,
          DEFAULT_HADOOP_HTTP_STATIC_USER);
      options.put(HADOOP_HTTP_STATIC_USER, username);
      HttpServer2.defineFilter(timelineRestServer.getWebAppContext(),
          "static_user_filter_timeline",
          StaticUserWebFilter.StaticUserFilter.class.getName(),
          options, new String[] {"/*"});

      timelineRestServer.addJerseyResourcePackage(
          TimelineCollectorWebService.class.getPackage().getName() + ";"
              + GenericExceptionHandler.class.getPackage().getName() + ";"
              + YarnJacksonJaxbJsonProvider.class.getPackage().getName(),
          "/*");
      timelineRestServer.setAttribute(COLLECTOR_MANAGER_ATTR_KEY,
          TimelineCollectorManager.getInstance());
      timelineRestServer.start();
    } catch (Exception e) {
      String msg = "The per-node collector webapp failed to start.";
      LOG.error(msg, e);
      throw new YarnRuntimeException(msg, e);
    }
  }

  private void reportNewCollectorToNM(ApplicationId appId)
      throws YarnException, IOException {
    this.nmCollectorService = getNMCollectorService();
    ReportNewCollectorInfoRequest request =
        ReportNewCollectorInfoRequest.newInstance(appId,
            this.timelineRestServerBindAddress);
    LOG.info("Report a new collector for application: " + appId +
        " to the NM Collector Service.");
    nmCollectorService.reportNewCollectorInfo(request);
  }

  @VisibleForTesting
  protected CollectorNodemanagerProtocol getNMCollectorService() {
    Configuration conf = getConfig();
    final YarnRPC rpc = YarnRPC.create(conf);

    // TODO Security settings.
    return (CollectorNodemanagerProtocol) rpc.getProxy(
        CollectorNodemanagerProtocol.class,
        nmCollectorServiceAddress, conf);
  }

  @VisibleForTesting
  public String getRestServerBindAddress() {
    return timelineRestServerBindAddress;
  }
}
