package io.tsdb.opentsdb.discovery;

import com.orbitz.consul.AgentClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.health.ServiceHealth;
import net.opentsdb.utils.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.tsdb.opentsdb.core.Utils.getConfigPropertyString;

public class ConsulHelper {
    private static Logger log = LoggerFactory.getLogger(ConsulPlugin.class);

    public static AgentClient getClient(final Config config) throws Exception {
        Consul consul = null;
        try {
            String consulUrl   = getConfigPropertyString(config, "tsd.discovery.consul_url", "http://localhost:8500");
            String serviceName = getConfigPropertyString(config, "tsd.discovery.service_name", "OpenTSDB");
            String serviceId   = getConfigPropertyString(config, "tsd.discovery.service_id", "opentsdb");
            String tsdMode     = getConfigPropertyString(config, "tsd.mode", "ro");

            log.debug("Finished with config");

            consul = Consul.builder().withUrl(consulUrl).build();
            log.info("Consul ServiceDiscovery Plugin Initialized");
        } catch (Exception e) {
            log.error("Could not register this instance with Consul", e);
        }
        if (consul != null) {
            return consul.agentClient();
        } else {
            throw new Exception("Could not initialize Consul");
        }
    }

    public static List<ServiceHealth> GetOpenTSDBNodes() {
        Consul consul = Consul.builder().build(); // connect to Consul on localhost
        HealthClient healthClient = consul.healthClient();

        return healthClient.getHealthyServiceInstances("DataService").getResponse();
    }
}
