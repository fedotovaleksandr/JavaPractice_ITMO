package ru.ifmo.ctddev.fedotov.concurrent;

import java.util.function.BinaryOperator;
import java.util.function.Supplier;

/**
 * Created by aleksandr on 15.12.16.
 */
public class Monoid<T> implements InterfaceMonoid<T> {
    private BinaryOperator<T> operator;
    private Supplier<T> neutralGen;

    public Monoid(BinaryOperator<T> operator, Supplier<T> neutralGen) {
        this.operator = operator;
        this.neutralGen = neutralGen;
    }

    @Override
    public T getNeutral() {
        return neutralGen.get();
    }

    @Override
    public T append(T a, T b) {
        return operator.apply(a, b);
    }

}
