package net.loyalnetwork.coffeelib.config.reload;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WatchServiceManager implements AutoCloseable {

    private WatchService watchService;
    private ExecutorService executor;

    public void start(Path configDir, Runnable onFileChange) {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            configDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY
            );
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Config-WatchService");
                t.setDaemon(true);
                return t;
            });
            executor.submit(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        WatchKey key = watchService.take();
                        for (WatchEvent<?> event : key.pollEvents()) {
                            if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                                onFileChange.run();
                            }
                        }
                        key.reset();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to start WatchService", e);
        }
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdownNow();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
        }
    }
}
