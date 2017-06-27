package io.tsdb.opentsdb.core;
/*
  Copyright 2015 The DiscoveryPlugins Authors
  <p/>
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
  <p/>
  http://www.apache.org/licenses/LICENSE-2.0
  <p/>
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

import net.opentsdb.core.IncomingDataPoint;
import net.opentsdb.tools.StartupPlugin;
import net.opentsdb.utils.Config;
import net.opentsdb.utils.PluginLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

public class Utils {
  private static final Logger LOG = LoggerFactory.getLogger(Utils.class);
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

  public static String getConfigPropertyString(Config config, String propertyName, String defaultValue) {
    String retVal = defaultValue;
    if (config.hasProperty(propertyName)) {
      retVal = config.getString(propertyName);
    }
    return retVal;
  }

  public static Integer getConfigPropertyInt(Config config, String propertyName, Integer defaultValue) {
    Integer retVal = defaultValue;
    if (config.hasProperty(propertyName)) {
      retVal = config.getInt(propertyName);
    }
    return retVal;
  }

  public static StartupPlugin loadStartupPlugin(Config config) {
    LOG.debug("Loading Startup Plugin");
    // load the startup plugin if enabled
    StartupPlugin startup;

    if (config.getBoolean("tsd.startup.enable")) {

      LOG.debug("startup plugin enabled");
      String startupPluginClass = config.getString("tsd.startup.plugin");

      LOG.debug(String.format("Will attempt to load: %s", startupPluginClass));
      startup = PluginLoader.loadSpecificPlugin(startupPluginClass
              , StartupPlugin.class);

      if (startup == null) {
        LOG.debug(String.format("2nd attempt will attempt to load: %s", startupPluginClass));
        startup = loadSpecificPlugin(config.getString("tsd.startup.plugin"), StartupPlugin.class);
        if (startup == null) {
          throw new IllegalArgumentException("Unable to locate startup plugin: " +
                  config.getString("tsd.startup.plugin"));
        }
      }

      try {
        startup.initialize(config);
      } catch (Exception e) {
        throw new RuntimeException("Failed to initialize startup plugin", e);
      }

      LOG.info("initialized startup plugin [" +
              startup.getClass().getCanonicalName() + "] version: "
              + startup.version());
    } else {
      startup = null;
    }

    return startup;
  }

  /**
   * @param name
   * @param type
   * @param <T>
   * @return
   */
  protected static <T> T loadSpecificPlugin(final String name,
                                            final Class<T> type) {
    LOG.debug("trying to find: " + name);
    if (name.isEmpty()) {
      throw new IllegalArgumentException("Missing plugin name");
    }
    ServiceLoader<T> serviceLoader = ServiceLoader.load(type);
    Iterator<T> it = serviceLoader.iterator();

    if (!it.hasNext()) {
      LOG.warn("Unable to locate any plugins of the type: " + type.getName());
      return null;
    }

    while(it.hasNext()) {
      T plugin = it.next();
      if (plugin.getClass().getName().toString().equals(name) || plugin.getClass().getSuperclass().getName().toString().equals(name)) {
        LOG.debug("matched!");
        return plugin;
      } else {
        LOG.debug(plugin.getClass().getName() + " and " +  plugin.getClass().getSuperclass() + " did not match: " + name);
      }
    }

    LOG.warn("Unable to locate locate plugin: " + name);
    return null;
  }

  public static String getMachineIP() {
    try {
      String hostIP = InetAddress.getLocalHost().getHostAddress();
      if (!hostIP.equals("127.0.0.1")) {
        return hostIP;
      }

        /*
         * Above method often returns "127.0.0.1", In this case we need to
         * check all the available network interfaces
         */
      Enumeration<NetworkInterface> nInterfaces = NetworkInterface
              .getNetworkInterfaces();
      while (nInterfaces.hasMoreElements()) {
        Enumeration<InetAddress> inetAddresses = nInterfaces
                .nextElement().getInetAddresses();
        while (inetAddresses.hasMoreElements()) {
          String address = inetAddresses.nextElement()
                  .getHostAddress();
          if (!address.equals("127.0.0.1")) {
            return address;
          }
        }
      }
    } catch (UnknownHostException | SocketException e1) {
      LOG.error(e1.getMessage());
    }
    return "127.0.0.1";
  }
}
