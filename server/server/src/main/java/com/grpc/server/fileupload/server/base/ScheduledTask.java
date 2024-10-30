package com.grpc.server.fileupload.server.base;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ScheduledTask {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ChordNode node;

    public ScheduledTask(ChordNode node) {
        this.node = node;
    }

    public void startScheduledTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                node.stabilize();
                node.fixFingers();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 20, TimeUnit.MILLISECONDS);
    }

    public void stopScheduledTask() {
        scheduler.shutdown();
    }
}