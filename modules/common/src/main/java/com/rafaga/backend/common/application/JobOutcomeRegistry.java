package com.rafaga.backend.common.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/** Keeps only the latest safe result for each scheduled job in this instance. */
@Component
public class JobOutcomeRegistry {
    private final ConcurrentMap<String, Outcome> latest = new ConcurrentHashMap<>();

    public void execute(String job, Runnable action) {
        try {
            action.run();
            latest.put(job, new Outcome(job, Status.SUCCESS, Instant.now()));
        } catch (RuntimeException exception) {
            latest.put(job, new Outcome(job, Status.FAILURE, Instant.now()));
            throw exception;
        }
    }

    public List<Outcome> snapshot() {
        return latest.values().stream()
                .sorted(Comparator.comparing(Outcome::job))
                .toList();
    }

    public enum Status { SUCCESS, FAILURE }

    public record Outcome(String job, Status status, Instant completedAt) { }
}
