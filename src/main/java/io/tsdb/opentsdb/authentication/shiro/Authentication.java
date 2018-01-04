package io.tsdb.opentsdb.authentication.shiro;

import com.stumbleupon.async.Deferred;
import net.opentsdb.auth.AuthState;
import net.opentsdb.auth.Authorization;
import net.opentsdb.core.TSDB;
import net.opentsdb.stats.StatsCollector;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * Created by jcreasy on 7/9/17.
 */
public class Authentication extends net.opentsdb.auth.Authentication {
    public Authentication() {

    }

    @Override
    public void initialize(TSDB tsdb) {

    }

    @Override
    public Deferred<Object> shutdown() {
        return null;
    }

    @Override
    public String version() {
        return null;
    }

    @Override
    public void collectStats(StatsCollector statsCollector) {

    }

    @Override
    public AuthState authenticateTelnet(Channel channel, String[] strings) {
        return null;
    }

    @Override
    public AuthState authenticateHTTP(Channel channel, HttpRequest httpRequest) {
        return null;
    }

    @Override
    public boolean isReady(TSDB tsdb, Channel channel) {
        return false;
    }
}
