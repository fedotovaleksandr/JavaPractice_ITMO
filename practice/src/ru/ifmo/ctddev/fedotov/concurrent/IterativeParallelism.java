package ru.ifmo.ctddev.fedotov.concurrent;

import info.kgeorgiy.java.advanced.concurrent.ScalarIP;
import ru.ifmo.ctddev.fedotov.mapper.ParallelMapperImpl;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Created by aleksandr on 14.12.16.
 */
public class IterativeParallelism implements ScalarIP {
    private ParallelMapperImpl mapper = null;

    public IterativeParallelism(ParallelMapperImpl mapper) {
        this.mapper = mapper;
    }

    @Override
    public <T> T maximum(int i, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        final InterfaceMonoid<T> monoid = new Monoid<T>((a, b) -> comparator.compare(a, b) >= 0 ? a : b, () -> list.get(0));
        int chunkSize = list.size() / i;
        Combinator<T, T> combinator = this.createCombinator((List<T>) list, chunkSize, monoid, UnaryOperator.identity());

        return combinator.process();
    }

    @Override
    public <T> T minimum(int i, List<? extends T> list, Comparator<? super T> comparator) throws InterruptedException {
        final InterfaceMonoid<T> monoid = new Monoid<T>((a, b) -> comparator.compare(a, b) <= 0 ? a : b, () -> list.get(0));
        int chunkSize = list.size() / i;
        Combinator<T, T> combinator = this.createCombinator((List<T>) list, chunkSize, monoid, UnaryOperator.identity());

        return combinator.process();
    }

    @Override
    public <T> boolean all(int i, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        final InterfaceMonoid<Boolean> monoid = new Monoid<>(Boolean::logicalAnd, () -> Boolean.TRUE);
        int chunkSize = list.size() / i;
        Combinator<T, Boolean> combinator = this.createCombinator((List<T>) list, chunkSize, monoid, predicate::test);
        return combinator.process();
    }

    @Override
    public <T> boolean any(int i, List<? extends T> list, Predicate<? super T> predicate) throws InterruptedException {
        final InterfaceMonoid<Boolean> monoid = new Monoid<>(Boolean::logicalAnd, () -> Boolean.FALSE);
        int chunkSize = list.size() / i;
        Combinator<T, Boolean> combinator = this.createCombinator((List<T>) list, chunkSize, monoid, predicate::test);
        return combinator.process();
    }

    private <T, R> Combinator<T, R> createCombinator(List<T> elements, int chunkSize, InterfaceMonoid<R> monoid, Function<T, R> functor) {
        if (this.mapper == null) {
            return new Combinator<>((List<T>) elements, chunkSize, monoid, functor);
        } else {
            return new Combinator<T, R>((List<T>) elements, chunkSize, monoid, functor, this.mapper);
        }
    }
}
