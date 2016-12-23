package ru.ifmo.ctddev.fedotov.mapper;

import javax.lang.model.type.UnionType;
import java.util.function.Function;

/**
 * Created by aleksandr on 22.12.16.
 */
public class FutureTask<R, T> {

    private Function<T, R> function;
    private T item;


    private R result = null;


    FutureTask(Function<T, R> function, T item) {

        this.function = function;
        this.item = item;
    }

    void doWork() {
        if (!canceled) {
            this.setStatus(Status.RUNNING);
            this.result = this.function.apply(this.item);
        }
        this.setStatus(Status.COMPLETE);
    }


    private boolean canceled = false;

    void cancel() {
        this.canceled = true;
    }

    public R getResult() {
        return result;
    }

    private Status status = Status.WAIT;

    public Status getStatus() {
        return status;
    }

    private synchronized void setStatus(Status status){
        this.status = status;
    }

    static enum Status {
        COMPLETE ,
        WAIT ,
         RUNNING ,

    }
}
