package io.tsdb.opentsdb.core;
/**
 * Copyright 2015 The DiscoveryPlugins Authors
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import net.opentsdb.core.IncomingDataPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import static io.tsdb.opentsdb.core.Utils.makeDatapoint;

public class DataPoints {
  private long timestamp;
  private String metric;
  private HashMap<String, String> tags;
  private ArrayList<IncomingDataPoint> dataPointList;

  public DataPoints(final String metric,
                    final long timestamp,
                    final HashMap<String, String> tags) {
    this.metric = metric;
    this.timestamp = timestamp;
    this.tags = tags;
    this.dataPointList = new ArrayList<IncomingDataPoint>();
  }

  public DataPoints(final String metric,
                    final long timestamp,
                    final String value,
                    final HashMap<String, String> tags) {
    this.metric = metric;
    this.timestamp = timestamp;
    this.tags = tags;
    this.dataPointList = new ArrayList<IncomingDataPoint>();
    this.dataPointList.add(makeDatapoint(metric, timestamp, Double.parseDouble(value), tags));
  }

  public DataPoints(final long timestamp, final IncomingDataPoint dp) {
    this.metric = dp.getMetric();
    this.timestamp = timestamp;
    this.tags = dp.getTags();
    this.dataPointList = new ArrayList<IncomingDataPoint>();
    this.dataPointList.add(dp);
  }

  public long getTimestamp() {
    return timestamp;
  }

  public String getMetric() {
    return metric;
  }

  public HashMap<String, String> getTags() {
    return tags;
  }

  public void addDatapoint(Long value) {
    this.dataPointList.add(makeDatapoint(this.metric, this.timestamp, value, this.tags));
  }

  public void addDatapoint(Double value) {
    this.dataPointList.add(makeDatapoint(this.metric, this.timestamp, value, this.tags));
  }

  public void addDatapoint(IncomingDataPoint dp) {
    this.dataPointList.add(dp);
  }

  public IncomingDataPoint getAvgValue() {
    Double value = 0d;
    Double avgValue = 0d;
    if (this.dataPointList.size() > 0) {
      for (IncomingDataPoint dp : this.dataPointList) {
        value += Double.parseDouble(dp.getValue());
      }
      avgValue = value / this.dataPointList.size();
    }
    return new IncomingDataPoint(this.metric, this.timestamp, Objects.toString(avgValue), this.tags);
  }
}
