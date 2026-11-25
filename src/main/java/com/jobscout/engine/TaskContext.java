package com.jobscout.engine;

/** Callback surface the engine uses to report progress/events and observe cancellation. */
public interface TaskContext {
    boolean cancelled();
    void stage(String stage);
    void progress(int pagesVisited, int urlsDiscovered);
    void event(String level, String stage, String code, String message);
}
