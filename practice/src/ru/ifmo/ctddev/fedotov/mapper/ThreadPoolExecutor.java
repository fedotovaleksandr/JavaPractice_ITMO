package ru.ifmo.ctddev.fedotov.mapper;

import java.util.*;
import java.util.function.Function;

/**
 * Created by aleksandr on 22.12.16.
 */
public class ThreadPoolExecutor {
    private Queue<FutureTask> tasks = new ArrayDeque<>(2048);
    private final int threadsCount;
    private List<Thread> threads = new ArrayList<>();
    final Object monitor = new Object();

    public ThreadPoolExecutor(final int threads) {
        this.threadsCount = threads;

        for (int i = 0; i < this.threadsCount; i++) {
            this.threads.add(new Thread(new FutureRunnable(this.tasks, monitor), "Thread" + i));
        }
    }

    public <T, R> Collection<FutureTask<R, T>> createTasks(Function<? super T, ? extends R> function, List<? extends T> list) {
        final Collection<FutureTask<R, T>> futureTasks = new ArrayList<>();
        for (T item : list) {
            futureTasks.add(this.createTask(function, item));
        }
        return null;
    }

    private <T, R> FutureTask createTask(Function<? super T, ? extends R> function, T item) {
        FutureTask task = new FutureTask<>(function, item);
        this.tasks.add(task);
        return task;
    }


    void shutdown() throws InterruptedException {
        this.cancelAllTasks();
        threads.forEach(Thread::interrupt);
        for (Thread thread : threads) {
            thread.join();
        }
    }
    private synchronized void cancelAllTasks(){
        tasks.forEach(FutureTask::cancel);
        tasks.clear();
    }

    void continueProcess() {
        this.monitor.notifyAll();
    }

    public boolean isComplete() {
        boolean answer = true;
        if (this.tasks.size() != 0){
            return false;
        }
        for (Thread th : this.threads) {
            answer &= th.getState() == Thread.State.WAITING;
        }
        return answer;
    }
}
