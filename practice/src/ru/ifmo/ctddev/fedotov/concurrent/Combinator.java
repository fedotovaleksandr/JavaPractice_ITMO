package ru.ifmo.ctddev.fedotov.concurrent;

import ru.ifmo.ctddev.fedotov.mapper.ParallelMapperImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Created by aleksandr on 15.12.16.
 */
public class Combinator<T, R> {
    private ParallelMapperImpl mapper = null;
    private List<T> elements;
    private int chunkSize;
    private InterfaceMonoid<R> monoid;
    private Function<T, R> functor;

    Combinator(List<T> elements, int chunkSize, InterfaceMonoid<R> monoid, Function<T, R> functor) {
        this.elements = elements;
        this.chunkSize = chunkSize > 0 ? chunkSize : 1;
        this.monoid = monoid;
        this.functor = functor;
    }

    public Combinator(List<T> elements,int chunkSize, InterfaceMonoid<R> monoid, Function<T, R> functor, ParallelMapperImpl mapper) {
        this.elements = elements;
        this.chunkSize = chunkSize;
        this.monoid = monoid;
        this.functor = functor;
        this.mapper = mapper;
    }

    public R process() throws InterruptedException {
        if (this.mapper != null){
            return this.processMapper();
        }
        Accumulator<R> accum = new Accumulator<>(this.monoid);
        List<Thread> threadsList = new ArrayList<>();
        for (int left = 0, index = 0; left < this.elements.size(); left += chunkSize, index++) {
            int right = Math.min(left + chunkSize, this.elements.size());
            List<T> subList = elements.subList(left, right);
            Thread newThread = new Thread(new MonoidRunnable<>(subList, this.monoid, accum, this.functor), "Thread" + index);
            threadsList.add(index, newThread);
            newThread.run();
        }

        for (Thread thread : threadsList
                ) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return accum.getValue();

    }

    private R processMapper() throws InterruptedException {

        Accumulator<R> accum = new Accumulator<>(this.monoid);

        for (int left = 0, index = 0; left < this.elements.size(); left += chunkSize, index++) {
            int right = Math.min(left + chunkSize, this.elements.size());
            List<T> subList = elements.subList(left, right);
            List<R> result = this.mapper.<T,R>map(this.functor,subList);
            for (R r : result) {
                accum.add(r);
            }
        }


        return accum.getValue();
    }
}
