package ru.ifmo.ctddev.fedotov.mapper;

import javax.lang.model.type.UnionType;
import java.util.concurrent.BlockingQueue;
import java.util.function.Function;

/**
 * Created by aleksandr on 22.12.16.
 */
public class FutureTask<R, T> {

    private Function<T, R> function;
    private T item;
    private int i;


    private R result = null;
    volatile BlockingQueue<FutureTask> resolvedTasks;

    public int getI() {
        return i;
    }

    FutureTask(Function<T, R> function, T item, int i,BlockingQueue<FutureTask> resolvedTasks) {
        this.resolvedTasks = resolvedTasks;
        this.function = function;
        this.item = item;
        this.i = i;
    }

    void doWork() throws InterruptedException {
        if (!canceled) {
            this.setStatus(Status.RUNNING);
            this.result = this.function.apply(this.item);
        }
        this.setStatus(Status.COMPLETE);
        this.resolvedTasks.put(this);
    }


    private boolean canceled = false;

    synchronized void cancel() {
        this.canceled = true;
    }

    public R getResult() {
        return result;
    }

    private Status status = Status.WAIT;

    public Status getStatus() {
        return status;
    }

    private synchronized void setStatus(Status status) {
        this.status = status;
    }

    static enum Status {
        COMPLETE,
        WAIT,
        RUNNING,

    }
}
