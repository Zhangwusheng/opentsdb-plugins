package io.tsdb.opentsdb.discovery;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import net.opentsdb.utils.Config;
import net.opentsdb.core.TSDB;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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

@RunWith(MockitoJUnitRunner.class)
public class IdentityPluginTest {

  private IdentityPlugin plugin;

  @Mock
  private Config mockedConfig;

  @Mock
  private Appender<ILoggingEvent> mockAppender;
  @Captor
  private ArgumentCaptor<LoggingEvent> captorLoggingEvent;

  @Before
  public void setup() {
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.addAppender(mockAppender);
    root.setLevel(Level.DEBUG);
    plugin = new IdentityPlugin();
  }

  @Test
  public void testInitialize() throws Exception {
    plugin.initialize(mockedConfig);
    verify(mockAppender, times(3)).doAppend(captorLoggingEvent.capture());
    LoggingEvent loggingEvent;
    List<LoggingEvent> logEvents = captorLoggingEvent.getAllValues();
    loggingEvent = logEvents.get(0);
    assertEquals("constructor called", loggingEvent.getMessage());
    assertEquals(Level.DEBUG, loggingEvent.getLevel());
    loggingEvent = logEvents.get(1);
    assertEquals("Apache Curator ServiceDiscovery Plugin Initialized", loggingEvent.getMessage());
    assertEquals(Level.INFO, loggingEvent.getLevel());
    loggingEvent = logEvents.get(2);
    assertEquals("Finished with config", loggingEvent.getMessage());
    assertEquals(Level.DEBUG, loggingEvent.getLevel());
  }

  @Test
  public void testShutdown() throws Exception {
    plugin.shutdown();
    verify(mockAppender, times(2)).doAppend(captorLoggingEvent.capture());
    LoggingEvent loggingEvent;
    List<LoggingEvent> logEvents = captorLoggingEvent.getAllValues();
    loggingEvent = logEvents.get(0);
    assertEquals("constructor called", loggingEvent.getMessage());
    assertEquals(Level.DEBUG, loggingEvent.getLevel());
    loggingEvent = logEvents.get(1);
    assertEquals("shutting down.", loggingEvent.getMessage());
    assertEquals(Level.DEBUG, loggingEvent.getLevel());
  }

  @Test
  public void testVersion() throws Exception {
    String version = plugin.version();
    assertEquals("2.0.0", version);
  }

  @Test
  public void testGetType() throws Exception {
    String type = plugin.getType();
    assertEquals("Curator Service Discovery", type);
  }

  @Test
  public void testPluginLogsProperly() throws Exception {

  }
}