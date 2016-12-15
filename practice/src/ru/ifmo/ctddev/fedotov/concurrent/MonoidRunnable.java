package ru.ifmo.ctddev.fedotov.concurrent;

import java.util.List;
import java.util.function.Function;

/**
 * Created by aleksandr on 15.12.16.
 */
public class MonoidRunnable<T,E> implements  Runnable{

    private List<T> subList;
    private InterfaceMonoid<E> monoid;
    private Accumulator<E> result;
    private Function<T, E> functor;

    MonoidRunnable(List<T> subList, InterfaceMonoid<E> monoid, Accumulator<E> result, Function<T, E> functor) {
        this.subList = subList;
        this.monoid = monoid;
        this.result = result;
        this.functor = functor;
    }

    @Override
    public void run() {
        Accumulator<E> accum = new Accumulator<>(this.monoid);
        for (T item:this.subList) {
            accum.add(this.functor.apply(item));
        }
        this.result.add(accum.getValue());
    }
}
