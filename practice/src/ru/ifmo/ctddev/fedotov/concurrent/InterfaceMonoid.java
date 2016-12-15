package ru.ifmo.ctddev.fedotov.concurrent;

import java.util.function.BinaryOperator;
import java.util.function.Supplier;

/**
 * Created by aleksandr on 15.12.16.
 */
public interface InterfaceMonoid<T> {
    public T getNeutral();
    public T append(T a, T b);

}
