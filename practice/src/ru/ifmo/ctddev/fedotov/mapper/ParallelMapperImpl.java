package ru.ifmo.ctddev.fedotov.mapper;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;
import ru.ifmo.ctddev.fedotov.concurrent.IterativeParallelism;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by aleksandr on 22.12.16.
 */
public class ParallelMapperImpl implements ParallelMapper {

    private final int threadsCount;
    private ThreadPoolExecutor threadPoolExecutor;

    public ParallelMapperImpl(int threads) {
        this.threadsCount = threads;
        this.threadPoolExecutor = new ThreadPoolExecutor(threads);
    }

    public int getThreadsCount() {
        return threadsCount;
    }

    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> function, List<? extends T> list) throws InterruptedException {
        try {
            return this.threadPoolExecutor.executeTasks(function, list);

        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Override
    public void close() throws InterruptedException {
        this.threadPoolExecutor.shutdown();
    }

}
