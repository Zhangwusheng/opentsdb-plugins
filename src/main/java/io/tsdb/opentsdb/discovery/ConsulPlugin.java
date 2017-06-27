package io.tsdb.opentsdb.discovery;

/*
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
import com.orbitz.consul.CatalogClient;
import com.orbitz.consul.Consul;
//import com.orbitz.consul.HealthClient;
//import com.orbitz.consul.model.health.ServiceHealth;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import com.orbitz.consul.model.catalog.CatalogService;
import com.orbitz.consul.model.health.ServiceHealth;
import org.kohsuke.MetaInfServices;
import com.stumbleupon.async.Deferred;
import net.opentsdb.core.TSDB;
import net.opentsdb.stats.StatsCollector;
import net.opentsdb.tools.StartupPlugin;
import net.opentsdb.utils.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import java.util.List;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static io.tsdb.opentsdb.core.Utils.getConfigPropertyInt;
import static io.tsdb.opentsdb.core.Utils.getConfigPropertyString;

@MetaInfServices
public class ConsulPlugin extends StartupPlugin {
    private final static Logger LOGGER = LoggerFactory.getLogger(ConsulPlugin.class);
    private static Consul consul;
    private static String tsdMode;
    private static String visibleHost;
    private static Integer visiblePort;
    private static String serviceName;
    private static String serviceId;

    @Override
    public Config initialize(final Config config) {
        try {
            visibleHost = getConfigPropertyString(config, "tsd.discovery.visble_host", "localhost");
            visiblePort = getConfigPropertyInt(config, "tsd.discovery.visble_port", 4242);
            serviceName = getConfigPropertyString(config, "tsd.discovery.service_name", "OpenTSDB");
            serviceId   = getConfigPropertyString(config, "tsd.discovery.service_id", "opentsdb");
            tsdMode     = getConfigPropertyString(config, "tsd.mode", "ro");

            String consulUrl = getConfigPropertyString(config, "tsd.discovery.consul_url", "http://localhost:8500");

            LOGGER.debug("Finished with config");

            consul = Consul.builder().withUrl(consulUrl).build();
            LOGGER.info("Consul ServiceDiscovery Plugin Initialized");

            updateZookeeperQuorum(config);

        } catch (Exception e) {
            LOGGER.error("Could not register this instance with Consul", e);
        }
        return config;
    }

    @Override
    public void setReady(TSDB tsdb) {
        LOGGER.debug("OpenTSDB is Ready");
        try {
            register();
        } catch (Exception e) {
            LOGGER.error("Could not register this instance with Consul", e);
        }
    }

    @Override
    public Deferred<Object> shutdown() {
        Deferred<Object> deferred = new Deferred<>();
        try {
            consul.agentClient().deregister(serviceId);
            LOGGER.info("Instance Deregistered from Consul");
        } catch (Exception e) {
            LOGGER.error("Could not deregister this instance with Consul", e);
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
        if (consul.agentClient().isRegistered(serviceId)) {
            LOGGER.debug("This instance is ready and registered with Consul");
            return true;
        } else {
            LOGGER.debug("Consul reports that this instance is not registered");
            return false;
        }
    }

    private static void register() {
        AgentClient agentClient = consul.agentClient();

        List<Registration.RegCheck> checks = new ArrayList<Registration.RegCheck>();

        HostAndPort serviceHostAndPort = HostAndPort.fromParts(visibleHost, visiblePort);

        Registration.RegCheck mainCheck = Registration.RegCheck.tcp(serviceHostAndPort.toString(), 30);

        checks.add(mainCheck);

        Registration registration = ImmutableRegistration
                .builder()
                .port(visiblePort)
                .address(visibleHost)
                .checks(checks)
                .name(serviceName)
                .id(serviceId)
                .addTags(tsdMode)
                .build();

        agentClient.register(registration);

        if (agentClient.isRegistered(serviceId)) {
            LOGGER.info("Registered this instance with Consul");
        } else {
            LOGGER.warn("Consul reports that this instance is not registered");
        }
    }

    private static List<CatalogService> getServiceNodes(String serviceName) {
        try {
            CatalogClient catalogClient = consul.catalogClient();
            ConsulResponse<List<CatalogService>> serviceResponse = catalogClient.getService(serviceName);
            return serviceResponse.getResponse();
        } catch (NullPointerException e) {
            LOGGER.error("Could not retrieve Consul Catalog Client");
            return null;
        }
    }

    private static String buildConnectionString(List<CatalogService> serviceList) {
        StringBuilder stringBuilder = new StringBuilder();

        Iterator<CatalogService> serviceListIterator = serviceList.iterator();
        while (serviceListIterator.hasNext()) {
            CatalogService serviceNode = serviceListIterator.next();

            stringBuilder
                    .append(serviceNode.getServiceAddress())
                    .append(":")
                    .append(serviceNode.getServicePort());

            if (serviceListIterator.hasNext()) {
                stringBuilder.append(",");
            }
        }

        return stringBuilder.toString();
    }

    private static void updateZookeeperQuorum(final Config config) {
        String zkQuorum;
        List<CatalogService> zookeeperService = null;

        zookeeperService = getServiceNodes("zookeeper-2181");
        if (zookeeperService.size() > 0) {
            zkQuorum = buildConnectionString(zookeeperService);
            LOGGER.info("Updated Zookeeper Quorum to " + zkQuorum);
            config.overrideConfig("tsd.storage.hbase.zk_quorum", zkQuorum);
        } else {
            LOGGER.info("Unable to locate zookeeper-2181 in Consul");
        }
    }

    static void deregister() {
        try {
            consul.agentClient().deregister(serviceId);
            LOGGER.info("Instance Deregistered from Consul");
        } catch (Exception e) {
            LOGGER.error("Could not deregister this instance with Consul", e);
        }
    }
}
