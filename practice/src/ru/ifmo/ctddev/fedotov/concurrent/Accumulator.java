package ru.ifmo.ctddev.fedotov.concurrent;

/**
 * Created by aleksandr on 15.12.16.
 */
public class Accumulator<T> {
    private InterfaceMonoid<T> monoid;
    private T val;

    public Accumulator(InterfaceMonoid<T> monoid) {
        this.monoid = monoid;
        this.val = monoid.getNeutral();
    }

    synchronized public void add(T second){
        this.val = this.monoid.append(this.val,second);
    }

    public T getValue(){
        return this.val;
    }
}
