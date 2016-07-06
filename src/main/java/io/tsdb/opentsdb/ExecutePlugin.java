package io.tsdb.opentsdb;
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

import net.opentsdb.core.TSDB;
import net.opentsdb.tools.ArgP;
import net.opentsdb.tsd.PipelineFactory;
import net.opentsdb.tsd.RpcManager;
import net.opentsdb.utils.Config;
import net.opentsdb.utils.PluginLoader;
import net.opentsdb.tools.StartupPlugin;
import net.opentsdb.utils.Threads;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerBossPool;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.channel.socket.oio.OioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ExecutePlugin {
  private static Logger LOG = LoggerFactory.getLogger(ExecutePlugin.class);
  private static final short DEFAULT_FLUSH_INTERVAL = 1000;

  public static void main(String[] args) throws IOException {
    LOG.info("Starting.");

    final ArgP argp = new ArgP();

    argp.addOption("--config", "PATH",
            "Path to a configuration file"
                    + " (default: Searches for file see docs).");
    args = argp.parse(args);

    // load configuration
    final Config config;
    final String config_file = argp.get("--config", "");
    if (!config_file.isEmpty())
      config = new Config(config_file);
    else
      config = new Config(true);

    final ServerSocketChannelFactory factory;
    int connections_limit = 0;
    try {
      connections_limit = config.getInt("tsd.core.connections.limit");
    } catch (NumberFormatException nfe) {
      nfe.printStackTrace();
    }
    if (config.getBoolean("tsd.network.async_io")) {
      int workers = Runtime.getRuntime().availableProcessors() * 2;
      if (config.hasProperty("tsd.network.worker_threads")) {
        try {
          workers = config.getInt("tsd.network.worker_threads");
        } catch (NumberFormatException nfe) {
          nfe.printStackTrace();
        }
      }
      final Executor executor = Executors.newCachedThreadPool();
      final NioServerBossPool boss_pool =
              new NioServerBossPool(executor, 1, new Threads.BossThreadNamer());
      final NioWorkerPool worker_pool = new NioWorkerPool(executor,
              workers, new Threads.WorkerThreadNamer());
      factory = new NioServerSocketChannelFactory(boss_pool, worker_pool);
    } else {
      factory = new OioServerSocketChannelFactory(
              Executors.newCachedThreadPool(), Executors.newCachedThreadPool(),
              new Threads.PrependThreadNamer());
    }

    StartupPlugin startup = null;
    startup = loadStartupPlugin(config);
    if (startup != null) {
      LOG.info(startup.version());
    } else {
      LOG.info("Did not load Startup Plugin");
    }

    TSDB tsdb = new TSDB(config);
//    startup.setReady(tsdb);
//    if (startup.getPluginReady()) {
//      LOG.info("Registered this instance with Consul");
//    } else {
//      LOG.info("Consul reports that this instance is not registered");
//    }

    tsdb.initializePlugins(true);
    if (config.getBoolean("tsd.storage.hbase.prefetch_meta")) {
      tsdb.preFetchHBaseMeta();
    }

    // Make sure we don't even start if we can't find our tables.
    try {
      tsdb.checkNecessaryTablesExist().joinUninterruptibly();
    } catch (Exception e1) {
      e1.printStackTrace();
    }

    //registerShutdownHook();
    final ServerBootstrap server = new ServerBootstrap(factory);

    // This manager is capable of lazy init, but we force an init
    // here to fail fast.
    final RpcManager manager = RpcManager.instance(tsdb);

    server.setPipelineFactory(new PipelineFactory(tsdb, manager, connections_limit));
    if (config.hasProperty("tsd.network.backlog")) {
      server.setOption("backlog", config.getInt("tsd.network.backlog"));
    }
    server.setOption("child.tcpNoDelay",
            config.getBoolean("tsd.network.tcp_no_delay"));
    server.setOption("child.keepAlive",
            config.getBoolean("tsd.network.keep_alive"));
    server.setOption("reuseAddress",
            config.getBoolean("tsd.network.reuse_address"));

    // null is interpreted as the wildcard address.
    InetAddress bindAddress = null;
    if (config.hasProperty("tsd.network.bind")) {
      bindAddress = InetAddress.getByName(config.getString("tsd.network.bind"));
    }

    // we validated the network port config earlier
    final InetSocketAddress addr = new InetSocketAddress(bindAddress,
            config.getInt("tsd.network.port"));
    server.bind(addr);
    if (startup != null) {
      startup.setReady(tsdb);
    }
    LOG.info("Ready to serve on " + addr);

    try {
      Thread.sleep(4000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    if (startup.getPluginReady()) {
      LOG.info("Registered this instance with Consul");
    } else {
      LOG.info("Consul reports that this instance is not registered");
    }
    try {
      Thread.sleep(4000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    if (startup.getPluginReady()) {
      LOG.info("Registered this instance with Consul");
    } else {
      LOG.info("Consul reports that this instance is not registered");
    }
    LOG.info("shutting down");
    if (startup != null) {
      startup.shutdown();
    }
    tsdb.shutdown();
    System.exit(0);
  }

  protected static StartupPlugin loadStartupPlugin(Config config) {
    // load the startup plugin if enabled
    StartupPlugin startup = null;
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
}