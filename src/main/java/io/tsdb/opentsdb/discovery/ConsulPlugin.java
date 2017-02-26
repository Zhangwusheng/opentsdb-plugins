package io.tsdb.opentsdb.discovery;

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

import com.google.common.net.HostAndPort;
import com.orbitz.consul.AgentClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.health.ServiceHealth;
import org.kohsuke.MetaInfServices;
import com.stumbleupon.async.Deferred;
import net.opentsdb.core.TSDB;
import net.opentsdb.stats.StatsCollector;
import net.opentsdb.tools.StartupPlugin;
import net.opentsdb.utils.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.tsdb.opentsdb.core.Utils.getConfigPropertyInt;
import static io.tsdb.opentsdb.core.Utils.getConfigPropertyString;

@MetaInfServices
public class ConsulPlugin extends StartupPlugin {
    private Logger log = LoggerFactory.getLogger(ConsulPlugin.class);
    private Consul consul;
    private String tsdMode;
    private String visibleHost;
    private Integer visiblePort;
    private String serviceName;
    private String serviceId;

    @Override
    public Config initialize(final Config config) {
        try {
            this.visibleHost = getConfigPropertyString(config, "tsd.discovery.visble_host", "localhost");
            this.visiblePort = getConfigPropertyInt(config, "tsd.discovery.visble_port", 4242);
            this.serviceName = getConfigPropertyString(config, "tsd.discovery.service_name", "OpenTSDB");
            this.serviceId   = getConfigPropertyString(config, "tsd.discovery.service_id", "opentsdb");
            this.tsdMode     = getConfigPropertyString(config, "tsd.mode", "ro");

            String consulUrl = getConfigPropertyString(config, "tsd.discovery.consul_url", "http://localhost:8500");

            log.debug("Finished with config");

            this.consul = Consul.builder().withUrl(consulUrl).build();
            log.info("Consul ServiceDiscovery Plugin Initialized");
        } catch (Exception e) {
            log.error("Could not register this instance with Consul", e);
        }
        return config;
    }

    @Override
    public void setReady(TSDB tsdb) {
        log.debug("OpenTSDB is Ready");
        try {
            HostAndPort tsdHostAndPort = HostAndPort.fromParts(visibleHost, visiblePort);
            AgentClient agentClient = this.consul.agentClient();
            agentClient.register(visiblePort, tsdHostAndPort, 30L, this.serviceName, this.serviceId, tsdMode);
            if (agentClient.isRegistered(this.serviceId)) {
                log.info("Registered this instance with Consul");
            } else {
                log.info("Consul reports that this instance is not registered");
            }
        } catch (Exception e) {
            log.error("Could not register this instance with Consul", e);
        }
    }

    @Override
    public Deferred<Object> shutdown() {
        Deferred<Object> deferred = new Deferred<>();
        try {
            this.consul.agentClient().deregister(this.serviceId);
            log.info("Instance Deregistered from Consul");
        } catch (Exception e) {
            log.error("Could not deregister this instance with Consul", e);
        }
        return deferred;
    }

    @Override
    public String version() { return "2.0.1"; }

    @Override
    public String getType() { return "Consul Service Discovery"; }

    @Override
    public void collectStats(StatsCollector statsCollector) { }

    @Override
    public boolean getPluginReady() {
        if (this.consul.agentClient().isRegistered(this.serviceId)) {
            log.debug("This instance is ready and registered with Consul");
            return true;
        } else {
            log.debug("Consul reports that this instance is not registered");
            return false;
        }
    }
}
