package io.tsdb.opentsdb.publishing;

import com.google.common.base.Joiner;
import com.stumbleupon.async.Deferred;
import net.opentsdb.core.TSDB;
import net.opentsdb.meta.Annotation;
import net.opentsdb.stats.StatsCollector;
import net.opentsdb.tsd.RTPublisher;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Relays to another OpenTSDB compatible instance
 */
public class RelayPublisher extends RTPublisher {
    private RelayClient client;
    private static final AtomicLong msgIn = new AtomicLong();
    private static final AtomicLong msgOut = new AtomicLong();

    @Override
    public void initialize(TSDB tsdb) {
        this.client = new RelayClient(tsdb);
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
    public void collectStats(StatsCollector statsCollector) {
        statsCollector.record("messages.input", msgIn.get());
        statsCollector.record("messages.output", msgOut.get());
    }

    @Override
    public Deferred<Object> publishDataPoint(final String metric, final long timestamp, final long value, final Map<String, String> tags, final byte[] tsuid) {
        msgIn.getAndIncrement();
        outputDataPoint(metric, timestamp, value, tags);
        return null;
    }

    @Override
    public Deferred<Object> publishDataPoint(final String metric, final long timestamp, final double value, final Map<String, String> tags, final byte[] tsuid) {
        msgIn.getAndIncrement();
        outputDataPoint(metric, timestamp, value, tags);
        return null;
    }

    @Override
    public Deferred<Object> publishAnnotation(Annotation annotation) {
        return null;
    }

    private String convertToPut(final String metric, final long timestamp, final double value, final Map<String, String> tagMap) {
        String joinedTags = Joiner.on(" ").withKeyValueSeparator("=").join(tagMap);
        return String.format("%s %d %d %s", metric, timestamp, value, joinedTags);
    }

    private void outputDataPoint(final String metric, final long timestamp, final double value, final Map<String, String> tags) {
        String msgToSend = convertToPut(metric, timestamp, value, tags);
        client.writeMessage(msgToSend);
        msgOut.getAndIncrement();
    }
}
