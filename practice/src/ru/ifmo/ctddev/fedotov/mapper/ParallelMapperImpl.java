package ru.ifmo.ctddev.fedotov.mapper;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Created by aleksandr on 22.12.16.
 */
public class ParallelMapperImpl implements ParallelMapper {

    private ThreadPoolExecutor threadPoolExecutor;

    public ParallelMapperImpl(int threads) {
        this.threadPoolExecutor = new ThreadPoolExecutor(threads);

    }

    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> function, List<? extends T> list) throws InterruptedException {
        final Collection<FutureTask<R, T>> futureTasks = this.threadPoolExecutor.createTasks(function, list);
        this.threadPoolExecutor.continueProcess();

        while (!this.threadPoolExecutor.isComplete()) {
            //wait
            boolean tasksComlete = true;
            for (FutureTask task : futureTasks
                    ) {
                tasksComlete &= task.getStatus() == FutureTask.Status.COMPLETE;

            }
            if (tasksComlete) {
                break;
            }
        }
        final List<R> result = new ArrayList<>();
        for (FutureTask<R, T> task : futureTasks
                ) {
            result.add(task.getResult());
        }
        return result;
    }

    @Override
    public void close() throws InterruptedException {
        this.threadPoolExecutor.shutdown();
    }


}
