package ru.ifmo.ctddev.fedotov.concurrent;

import info.kgeorgiy.java.advanced.concurrent.ScalarIP;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 *
 * Created by aleksandr on 14.12.16.
 */
public class IterativeParallelism implements ScalarIP {
    @Override
    public <T> T maximum(int i, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        final InterfaceMonoid<T> monoid = new Monoid<T>((a, b) -> comparator.compare(a, b) > 0 ? a : b, () -> list.get(0));
        int chunkSize = list.size() / i;
        Combinator<T,T> combinator = new Combinator<>((List<T>) list, chunkSize, monoid,UnaryOperator.identity());

        return combinator.process();
    }

    @Override
    public <T> T minimum(int i, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        final InterfaceMonoid<T> monoid = new Monoid<T>((a, b) -> comparator.compare(a, b) > 0 ? a : b, () -> list.get(0));
        int chunkSize = list.size() / i;
        Combinator<T,T> combinator = new Combinator<>((List<T>) list, chunkSize, monoid, UnaryOperator.identity());

        return combinator.process();
    }

    @Override
    public <T> boolean all(int i, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        final InterfaceMonoid<Boolean> monoid = new Monoid<>( Boolean::logicalAnd , ()->Boolean.TRUE );
        int chunkSize = list.size() / i;
        Combinator<T,Boolean> combinator = new Combinator<>((List<T>) list, chunkSize, monoid, predicate::test);
        return combinator.process();
    }

    @Override
    public <T> boolean any(int i, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        final InterfaceMonoid<Boolean> monoid = new Monoid<>(Boolean::logicalAnd, () -> Boolean.FALSE);
        int chunkSize = list.size() / i;
        Combinator<T,Boolean> combinator = new Combinator<>((List<T>) list, chunkSize, monoid, predicate::test);
        return combinator.process();
    }
}
