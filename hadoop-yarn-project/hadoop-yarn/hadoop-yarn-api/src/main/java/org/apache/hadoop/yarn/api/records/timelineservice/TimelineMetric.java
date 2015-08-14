/*
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
package org.apache.hadoop.yarn.api.records.timelineservice;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@XmlRootElement(name = "metric")
@XmlAccessorType(XmlAccessType.NONE)
@InterfaceAudience.Public
@InterfaceStability.Unstable
public class TimelineMetric {
  private String id;
  private Map<String, Object> info = new HashMap<>();
  private Object singleData;
  private Map<Long, Object> timeSeries = new LinkedHashMap<>();
  private long startTime;
  private long endTime;

  public TimelineMetric() {

  }

  @XmlElement(name = "id")
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @XmlElement(name = "info")
  public Map<String, Object> getInfo() {
    return info;
  }

  public void setInfo(Map<String, Object> info) {
    this.info = info;
  }

  public void addInfo(Map<String, Object> info) {
    this.info.putAll(info);
  }

  public void addInfo(String key, Object value) {
    info.put(key, value);
  }

  @XmlElement(name = "data")
  public Object getSingleData() {
    return singleData;
  }

  public void setSingleData(Object singleData) {
    this.singleData = singleData;
  }

  @XmlElement(name = "timeseries")
  public Map<Long, Object> getTimeSeries() {
    return timeSeries;
  }

  public void setTimeSeries(Map<Long, Object> timeSeries) {
    this.timeSeries = timeSeries;
  }

  public void addTimeSeries(Map<Long, Object> timeSeries) {
    this.timeSeries.putAll(timeSeries);
  }

  public void addTimeSeriesData(long timestamp, Object value) {
    timeSeries.put(timestamp, value);
  }

  @XmlElement(name = "starttime")
  public long getStartTime() {
    return startTime;
  }

  public void setStartTime(long startTime) {
    this.startTime = startTime;
  }

  @XmlElement(name = "endtime")
  public long getEndTime() {
    return endTime;
  }

  public void setEndTime(long endTime) {
    this.endTime = endTime;
  }
}
