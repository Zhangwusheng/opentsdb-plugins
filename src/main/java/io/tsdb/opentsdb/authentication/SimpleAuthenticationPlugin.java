package io.tsdb.opentsdb.authentication;
// This file is part of OpenTSDB.
// Copyright (C) 2010-2012  The OpenTSDB Authors.
//
// This program is free software: you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 2.1 of the License, or (at your
// option) any later version.  This program is distributed in the hope that it
// will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
// of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
// General Public License for more details.  You should have received a copy
// of the GNU Lesser General Public License along with this program.  If not,
// see <http://www.gnu.org/licenses/>.


import com.stumbleupon.async.Deferred;
import net.opentsdb.auth.AuthState;
import net.opentsdb.auth.SimpleAuthStateImpl;
import net.opentsdb.auth.Authentication;
import net.opentsdb.auth.Authorization;
import net.opentsdb.core.TSDB;
import net.opentsdb.stats.StatsCollector;
import net.opentsdb.utils.DateTime;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @since 2.3
 */
@MetaInfServices
public class SimpleAuthenticationPlugin extends Authentication {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleAuthenticationPlugin.class);
    private Map<String, String> authDB = new HashMap();
    private String adminAccessKey = null;
    private String adminSecretKey = null;

    @Override
    public void initialize(TSDB tsdb) {
        LOG.debug("Initialized Authentication Plugin");
        this.adminAccessKey = tsdb.getConfig().getString("tsd.core.authentication.admin_access_key");
        this.adminSecretKey = tsdb.getConfig().getString("tsd.core.authentication.admin_access_secret");
        storeCredentials(this.adminAccessKey, this.adminSecretKey);
    }

    @Override
    public Deferred<Object> shutdown() {
        return null;
    }

    @Override
    public String version() {
        return "2.3.0";
    }

    @Override
    public void collectStats(StatsCollector collector) {

    }

    @Override
    public AuthState authenticateTelnet(Channel channel, String[] strings) {
        AuthState ret;
        try {
            if (strings.length  < 3 || strings.length > 4) {
                throw new IllegalArgumentException("Invalid Authentication Command Length: " + Integer.toString(strings.length));
            } else if (strings[0].equals("auth")) {
                if (strings[1].equals(AuthenticationUtil.algo.trim().toLowerCase())) {
                    // Command should be 'auth hmacsha256 accessKey:digest:epoch:nonce'
                    Map<String, String> fields = AuthenticationUtil.stringToMap(strings[2], ":");
                    LOG.debug("Validating Digest Credentials");
                    if(this.authenticate((String) fields.get("accessKey"), fields)) {
                        ret = new SimpleAuthStateImpl((String) fields.get("accessKey"), AuthState.AuthStatus.SUCCESS, "Access Granted");
                    } else {
                        ret = new SimpleAuthStateImpl((String) fields.get("accessKey"), AuthState.AuthStatus.FORBIDDEN, "Access Denied");
                    }
                } else if (strings[1].equals("basic")) {
                    // Command should be 'auth basic accessKey secretAccessKey'
                    LOG.debug("Validating Basic Credentials");
                    if (this.authenticate(strings[2], strings[3])) {
                        ret = new SimpleAuthStateImpl(strings[2], AuthState.AuthStatus.SUCCESS, "Access Granted");
                    } else {
                        ret = new SimpleAuthStateImpl(strings[2], AuthState.AuthStatus.FORBIDDEN, "Access Denied");
                    }
                } else {
                    throw new IllegalArgumentException("Command not understood: " + strings[0] + " " + strings[1]);
                }
            } else {
                throw new IllegalArgumentException("Command is not auth: " + strings[0]);
            }
        } catch (IllegalArgumentException ex) {
            ret = new SimpleAuthStateImpl("unknown", ex);
        }
        return ret;
    }

    @Override
    public AuthState authenticateHTTP(Channel channel, HttpRequest httpRequest) {
        AuthState ret = null;
        try {
            Iterable<Map.Entry<String, String>> headers = httpRequest.headers();
            Iterator entries = headers.iterator();
            while (entries.hasNext()) {
                Map.Entry thisEntry = (Map.Entry) entries.next();
                String key = (String) thisEntry.getKey();
                String value = (String) thisEntry.getValue();
                if (key.trim().toLowerCase().equals("authorization")) {
                    String[] fieldsRaw = value.split(" ");
                    if (fieldsRaw.length == 2 && fieldsRaw[0].trim().toLowerCase().equals("opentsdb")) {
                        String[] fieldsArray = fieldsRaw[1].trim().toLowerCase().split(":");
                        Map<String, String> fields = AuthenticationUtil.stringToMap(fieldsRaw[1], ":");
                        LOG.debug("Validating Digest Credentials");

                        if (this.authenticate((String) fields.get("accessKey"), fields)) {
                            if(this.authenticate((String) fields.get("accessKey"), fields)) {
                                ret = new SimpleAuthStateImpl((String) fields.get("accessKey"), AuthState.AuthStatus.SUCCESS, "Access Granted");
                            } else {
                                ret = new SimpleAuthStateImpl((String) fields.get("accessKey"), AuthState.AuthStatus.FORBIDDEN, "Access Denied");
                            }
                        }
                    } else {
                        throw new IllegalArgumentException("Improperly formatted Authorization Header: " + value);
                    }
                }
            }
            if (ret == null) {
                throw new IllegalArgumentException("No Authorization Header Found");
            }
        } catch (IllegalArgumentException ex) {
            ret = new SimpleAuthStateImpl("unknown", ex);
        }
        return ret;
    }

    @Override
    public boolean isReady(TSDB tsdb, Channel channel) {
        return false;
    }

    public Boolean storeCredentials(Map fields) {
        String accessKey = (String) fields.get("accessKey");
        String providedSecretKey = (String) fields.get("secretKey");
        return this.storeCredentials(accessKey, providedSecretKey);
    }

    public Boolean removeCredentials(Map fields) {
        String adminAccessKey = (String) fields.get("adminAccessKey");
        String adminSecretKey = (String) fields.get("adminSecretKey");
        String accessKey = (String) fields.get("accessKey");
        return this.removeCredentials(adminAccessKey, adminSecretKey, accessKey);
    }

    private Boolean authenticate(String accessKey, Map fields) {
        try {
            AuthenticationUtil.validateFields(fields);
            String providedDigest = (String) fields.get("digest");
            LOG.debug("Authenticating " + accessKey + " " + providedDigest);
            Long providedTimestamp = DateTime.parseDateTimeString((String) fields.get("date"), "UTC");
            Long minimumTimestamp = DateTime.parseDateTimeString("20m-ago", "UTC");
            if (providedTimestamp < minimumTimestamp) {
                throw new IllegalArgumentException("Provided timestamp: " + (String) fields.get("date") + " is too old.");
            } else {
                String calculatedDigest = AuthenticationUtil.createDigest(new EmbeddedAccessKeyPair(accessKey, authDB.get(accessKey)), fields);
                LOG.debug("Calc: " + calculatedDigest);
                LOG.debug("Prov: " + providedDigest);
                return AuthenticationUtil.validateCredentials(accessKey, calculatedDigest, accessKey, providedDigest);
            }
        } catch (Exception e) {
            LOG.error("Exception: " + e);
            return false;
        }
    }

    private Boolean authenticate(String providedAccessKey, String providedSecretKey) {
        String correctSecretKey = (String) authDB.get(providedAccessKey);
        return AuthenticationUtil.validateCredentials(providedAccessKey, correctSecretKey, providedAccessKey, providedSecretKey);
    }

    private Boolean authenticateAdmin(String providedAdminAccessKey, String providedAdminSecretKey) {
        return AuthenticationUtil.validateCredentials(this.adminAccessKey, this.adminSecretKey, providedAdminAccessKey,providedAdminSecretKey);
    }

    private Boolean storeCredentials(String accessKey, String accessSecretKey) {
        try {
            authDB.put(accessKey, accessSecretKey);
        } catch (Exception e) {
            LOG.error("Exception: " + e);
            return false;
        } finally {
            return true;
        }
    }

    private Boolean removeCredentials(String adminAccessKey, String adminSecretKey, String accessKey) {
        try {
            if (authenticateAdmin(adminAccessKey, adminSecretKey)) {
                authDB.remove(accessKey);
            } else {
                return false;
            }
        } catch (Exception e) {
            LOG.error("Exception: " + e);
            return false;
        } finally {
            return true;
        }
    }
}
