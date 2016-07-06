package io.tsdb.opentsdb.realtime;

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

import com.stumbleupon.async.Deferred;
import io.tsdb.opentsdb.core.DataPoints;
import net.opentsdb.core.IncomingDataPoint;
import net.opentsdb.core.TSDB;
import net.opentsdb.meta.Annotation;
import net.opentsdb.stats.StatsCollector;
import net.opentsdb.tsd.RTPublisher;
import net.opentsdb.utils.Config;
import org.apache.commons.codec.digest.DigestUtils;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static io.tsdb.opentsdb.core.Utils.floorTimestamp;
import static io.tsdb.opentsdb.core.Utils.getTagString;
import static io.tsdb.opentsdb.core.Utils.makeDatapoint;

@MetaInfServices
public class RollupPublisher extends RTPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(RollupPublisher.class);
  private Map<String, DataPoints> dataPointsMap;
  private int minutes = 5;
  private String rollupKey = "tsd.rtpublisher.rollup.window";
  private TSDB tsdb;

  public void initialize(final TSDB tsdb) {
    LOG.info("init RollupPublisher");
    this.tsdb = tsdb;
    this.dataPointsMap = new HashMap<String, DataPoints>();
    Config config = tsdb.getConfig();
    if (config.hasProperty(rollupKey)) {
      this.minutes = tsdb.getConfig().getInt(rollupKey);
    }
    LOG.info("Using window of:" + this.minutes + " minutes");
  }

  public Deferred<Object> shutdown() {
    this.storeRollups();
    return null;
  }

  public String version() {
    return "2.3.0";
  }

  public void collectStats(final StatsCollector collector) {
  }

  public Deferred<Object> publishDataPoint(final String metric,
                                           final long timestamp, final long value, final Map<String, String> tags,
                                           final byte[] tsuid) {
    LOG.trace("Storing Datapoint: " + metric + " " + timestamp + " " + value);
    IncomingDataPoint dp = makeDatapoint(metric + "." + Objects.toString(this.minutes) + "m-avg", timestamp, value, tags);
    storeDatapoint(dp);
    return new Deferred<Object>();
  }

  public Deferred<Object> publishDataPoint(final String metric,
                                           final long timestamp, final double value, final Map<String, String> tags,
                                           final byte[] tsuid) {
    LOG.trace("Storing Datapoint: " + metric + " " + timestamp + " " + value);
    IncomingDataPoint dp = makeDatapoint(metric + "." + Objects.toString(this.minutes) + "m-avg", timestamp, value, tags);
    storeDatapoint(dp);
    return new Deferred<Object>();
  }

  @Override
  public Deferred<Object> publishAnnotation(Annotation annotation) {
    return null;
  }

  private void storeDatapoint(final IncomingDataPoint dp) {
    long ts = floorTimestamp(new Date(dp.getTimestamp()), this.minutes).getTime();
    String tagString = getTagString(dp.getTags());
    String key = DigestUtils.md5Hex(dp.getMetric() + Objects.toString(ts) + tagString);
    LOG.trace("Key evaluates to: " + key);
    if (!this.dataPointsMap.containsKey(key)) {
      LOG.trace("adding new dps key, timestamp: " + ts);
      this.dataPointsMap.put(key, new DataPoints(ts, dp));
    } else {
      LOG.trace("adding to existing dps, timestamp: " + ts);
      DataPoints dps = this.dataPointsMap.get(key);
      dps.addDatapoint(dp);
      this.dataPointsMap.replace(key, dps);
    }
  }

  private void storeRollups() {
    if (this.dataPointsMap.size()== 0) {
      LOG.debug("No DataPoints to consider for rollup");
      return;
    }
    LOG.debug("Considering " + this.dataPointsMap.size() + " DataPoints for rollup");
    long maximumTS = floorTimestamp(new Date(), this.minutes).getTime();
    for (Map.Entry<String, DataPoints> entry : this.dataPointsMap.entrySet()) {
      DataPoints dps = entry.getValue();
      if (dps.getTimestamp() < (maximumTS)) {
        IncomingDataPoint avgDP = dps.getAvgValue();
        LOG.debug( "Key: " + entry.getKey() + " Metric: " + dps.getMetric() +
                " Timestamp: " + Objects.toString(dps.getTimestamp()) +
                " Tags: " + getTagString(dps.getTags()) + " Avg: " + avgDP.getValue());
        LOG.trace("removing " + entry.getKey() + " from dataPointsMap");
        this.dataPointsMap.remove(entry.getKey());
      }
    }
  }
}