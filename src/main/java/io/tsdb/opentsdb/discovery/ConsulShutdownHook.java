package io.tsdb.opentsdb.discovery;

public class ConsulShutdownHook extends Thread {
    public void run() {
        ConsulPlugin.deregister();
    }
}