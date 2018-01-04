package io.tsdb.opentsdb.authentication.shiro;

import com.stumbleupon.async.Deferred;
import net.opentsdb.auth.AuthState;
import net.opentsdb.auth.Roles;
import net.opentsdb.core.IncomingDataPoint;
import net.opentsdb.core.TSDB;
import net.opentsdb.core.TSQuery;
import net.opentsdb.query.pojo.Query;
import net.opentsdb.stats.StatsCollector;

/**
 * Created by jcreasy on 1/4/18.
 */
public class Authorization extends net.opentsdb.auth.Authorization {
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
    public boolean hasPermission(AuthState authState, Roles.Permissions permissions) {
        return false;
    }

    @Override
    public boolean hasRole(AuthState authState, Roles roles) {
        return false;
    }

    @Override
    public AuthState allowQuery(AuthState authState, TSQuery tsQuery) {
        return null;
    }

    @Override
    public AuthState allowQuery(AuthState authState, Query query) {
        return null;
    }

    @Override
    public AuthState allowWrite(AuthState authState, IncomingDataPoint incomingDataPoint) {
        return null;
    }
}
