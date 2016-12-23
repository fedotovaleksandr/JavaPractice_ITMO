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

    public static void main(String[] args) throws InterruptedException {
        ParallelMapperImpl pm = new ParallelMapperImpl(2);
        IterativeParallelism it = new IterativeParallelism(pm);

        Integer result = it.<Integer>maximum(3,pm.randomList(100), Comparator.<Integer>comparingInt(v -> v / 100));
        System.out.print(result);
        pm.close();

    }

    private final Random random = new Random(3257083275083275083L);
    protected  List<Integer> randomList(final int size) {
        final List<Integer> pool = random.ints(Math.min(size, 1000_000)).boxed().collect(Collectors.toList());
        final List<Integer> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            result.add(pool.get(random.nextInt(pool.size())));
        }
        return result;
    }
}
