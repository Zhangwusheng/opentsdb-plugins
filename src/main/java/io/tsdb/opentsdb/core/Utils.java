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

import io.tsdb.opentsdb.realtime.RollupPublisher;
import net.opentsdb.core.IncomingDataPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Utils {
  private static final Logger LOG = LoggerFactory.getLogger(RollupPublisher.class);
  public static Date floorTimestamp(Date ts, int windowMinutes) {
    Calendar calendar = Calendar.getInstance();
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    int modulo = calendar.get(Calendar.MINUTE) % windowMinutes;
    if(modulo > 0) {
      calendar.add(Calendar.MINUTE, -modulo);
    }
    long finalTS = calendar.getTime().getTime() / 1000;
    return new Date(finalTS);
  }
  public static IncomingDataPoint makeDatapoint(final String metric,
                                                 final long timestamp, final long value, final Map<String, String> tags) {
    HashMap<String, String> tagsHash = new HashMap<String, String>(tags);
    return new IncomingDataPoint(metric, timestamp, Objects.toString(value, null), tagsHash);
  }

  public static IncomingDataPoint makeDatapoint(final String metric,
                                                 final long timestamp, final double value, final Map<String, String> tags) {
    HashMap<String, String> tagsHash = new HashMap<String, String>(tags);
    return new IncomingDataPoint(metric, timestamp, Objects.toString(value, null), tagsHash);
  }

  public static String getTagString(HashMap<String, String> tags) {
    String tagString = "";
    for (Map.Entry<String, String> entry : tags.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      tagString += key + value;
    }
    return tagString;
  }
}
