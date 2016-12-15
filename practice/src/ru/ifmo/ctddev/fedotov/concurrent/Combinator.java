package ru.ifmo.ctddev.fedotov.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Created by aleksandr on 15.12.16.
 */
public class Combinator<T,E> {
    private List<T> elements;
    private int chunkSize;
    private InterfaceMonoid<E> monoid;
    private Function<T, E> functor;

    Combinator(List<T> elements, int chunkSize, InterfaceMonoid<E> monoid, Function<T,E> functor) {
        this.elements = elements;
        this.chunkSize = chunkSize;
        this.monoid = monoid;
        this.functor = functor;
    }

    public E process() {
        Accumulator<E> accum = new Accumulator<>(this.monoid);
        List<Thread> threadsList = new ArrayList<>();
        for (int left = 0, index = 0; left < this.elements.size(); left += chunkSize, index++) {
            int right = Math.min(left + chunkSize, this.elements.size());
            List<T> subList = elements.subList(left, right);
            Thread newThread = new Thread(new MonoidRunnable<>(subList, this.monoid, accum, this.functor), "Thread" + index);
            threadsList.add(index,newThread);
            newThread.run();
        }

        for (Thread thread:threadsList
             ) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return accum.getValue();

    }
}
