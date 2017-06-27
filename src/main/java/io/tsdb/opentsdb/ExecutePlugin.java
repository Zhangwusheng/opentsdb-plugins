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

import io.netty.handler.timeout.TimeoutException;
import io.tsdb.opentsdb.discovery.ConsulShutdownHook;
import net.opentsdb.core.TSDB;
import net.opentsdb.tools.ArgP;
import net.opentsdb.tools.StartupPlugin;
import net.opentsdb.tsd.PipelineFactory;
import net.opentsdb.tsd.RpcManager;
import net.opentsdb.utils.Config;
import net.opentsdb.utils.PluginLoader;
import net.opentsdb.utils.Threads;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerBossPool;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.channel.socket.oio.OioServerSocketChannelFactory;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.LogManager;
import org.slf4j.bridge.SLF4JBridgeHandler;


import static io.tsdb.opentsdb.core.Utils.*;

public class ExecutePlugin {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ExecutePlugin.class);
    private static TSDB tsdb = null;

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

        ExecutePlugin.run(config);
    }

    private static void run(Config config) {
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        java.util.logging.Logger.getLogger("global").setLevel(Level.FINEST);
        LOG.debug("Running");
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

        StartupPlugin startup = loadStartupPlugin(config);
        if (startup != null) {
            LOG.info(startup.version());
            Runtime.getRuntime().addShutdownHook(new ConsulShutdownHook());
        } else {
            LOG.info("Did not load Startup Plugin");
        }

        tsdb = new TSDB(config);

        if (startup != null) {
            tsdb.setStartupPlugin(startup);
        }

        tsdb.initializePlugins(true);
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

        registerShutdownHook();
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
            try {
                bindAddress = InetAddress.getByName(config.getString("tsd.network.bind"));
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }

        // we validated the network port config earlier
        final InetSocketAddress addr = new InetSocketAddress(bindAddress,
                config.getInt("tsd.network.port"));
        server.bind(addr);
        if (startup != null) {
            startup.setReady(tsdb);
        }
        LOG.info("Ready to serve on " + addr);

        int tickCount = 0;
        int STARTUP_READY_TIMEOUT = 30;

        if (startup != null) {
            while (!startup.getPluginReady()) {
                try {
                    if (tickCount >= STARTUP_READY_TIMEOUT) {
                        throw new TimeoutException("Timeout while waiting for Startup Plugin Ready");
                    }

                    Thread.sleep(1000);
                    tickCount++;
                } catch (InterruptedException e) {
                    LOG.error("Startup Plugin ready interrupted", e);
                    break;
                } catch (TimeoutException e) {
                    LOG.warn("Startup Plugin is not Ready and timeout expired!");
                    break;
                }
            }
            if (startup.getPluginReady()) {
                LOG.info("Startup Plugin is Ready");
            }
        }
    }

    private static void registerShutdownHook() {
        final class TSDBShutdown extends Thread {
            private TSDBShutdown() {
                super("TSDBShutdown");
            }

            public void run() {
                try {
                    if (RpcManager.isInitialized()) {
                        // Check that its actually been initialized.  We don't want to
                        // create a new instance only to shutdown!
                        RpcManager.instance(tsdb).shutdown().join(10000);
                    }
                    if (tsdb != null) {
                        tsdb.shutdown();
                    }
                } catch (Exception e) {
                    LoggerFactory.getLogger(TSDBShutdown.class)
                            .error("Uncaught exception during shutdown", e);
                }
            }
        }
        Runtime.getRuntime().addShutdownHook(new TSDBShutdown());
    }

}