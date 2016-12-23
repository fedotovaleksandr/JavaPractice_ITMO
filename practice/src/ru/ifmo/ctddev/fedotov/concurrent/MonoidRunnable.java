package ru.ifmo.ctddev.fedotov.concurrent;

import ru.ifmo.ctddev.fedotov.mapper.ParallelMapperImpl;

import java.util.List;
import java.util.function.Function;

/**
 * Created by aleksandr on 15.12.16.
 */
public class MonoidRunnable<T, R> implements Runnable {


    private List<T> subList;
    private InterfaceMonoid<R> monoid;
    private Accumulator<R> result;
    private Function<T, R> functor;

    MonoidRunnable(List<T> subList, InterfaceMonoid<R> monoid, Accumulator<R> result, Function<T, R> functor) {
        this.subList = subList;
        this.monoid = monoid;
        this.result = result;
        this.functor = functor;
    }


    @Override
    public void run() {
        Accumulator<R> accum = new Accumulator<>(this.monoid);


        for (T item : this.subList) {
            accum.add(this.functor.apply(item));
        }


        this.result.add(accum.getValue());
    }
}
