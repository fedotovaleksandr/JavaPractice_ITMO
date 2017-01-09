package ru.ifmo.ctddev.fedotov.mapper;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;

/**
 * Created by aleksandr on 22.12.16.
 */
public class ThreadPoolExecutor {
    private volatile BlockingQueue<FutureTask> tasks = new LinkedBlockingQueue<>();
    private volatile BlockingQueue<FutureTask> resolvedTasks = new LinkedBlockingQueue<>();
    private final int threadsCount;
    private List<Thread> threads = new ArrayList<>();
    private final Object monitor = new Object();
    private final Object headMonitor = new Object();
    ;

    public ThreadPoolExecutor(final int threads) {
        this.threadsCount = threads;

        for (int i = 0; i < this.threadsCount; i++) {
            this.threads.add(new Thread(new FutureRunnable(this.tasks), "Thread" + i));
        }
    }

    public <T, R> Collection<FutureTask<R, T>> createTasks(Function<? super T, ? extends R> function, List<? extends T> list, BlockingQueue<FutureTask> resolvedTasks) {
        final Collection<FutureTask<R, T>> futureTasks = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            futureTasks.add(this.createTask(function, list.get(i), i,resolvedTasks));
        }
        return futureTasks;
    }

    private <T, R> FutureTask createTask(Function<? super T, ? extends R> function, T item, int i, BlockingQueue<FutureTask> resolvedTasks) {
        FutureTask task = new FutureTask<>(function, item, i,resolvedTasks);
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

    private synchronized void cancelAllTasks() {
        tasks.forEach(FutureTask::cancel);
        //tasks.clear();
    }

    void continueProcess() {
        this.wakeUpNew();
    }

    private void wakeUpNew() {
        for (Thread th : this.threads) {
            if (th.getState() == Thread.State.NEW) {
                th.start();
            }
        }
    }

    public void wakeUpInterupt() {
        int waitingThreads = 0;
        for (int i = 0; i < this.threads.size(); i++) {
            Thread th = this.threads.get(i);
            Thread.State threadState = th.getState();
            if (threadState == Thread.State.TERMINATED) {
                th.interrupt();
                Thread newthread = new Thread(new FutureRunnable(this.tasks), "Thread" + i);
                this.threads.set(i, newthread);
                newthread.start();
            }
            if (threadState == Thread.State.WAITING) {
                waitingThreads++;
            }
        }
        if (waitingThreads == this.threads.size() && !this.tasks.isEmpty()) {
            this.continueProcess();
        }
    }

    public <R, T> List<R> executeTasks(Function<? super T, ? extends R> function, List<? extends T> list) throws InterruptedException {
        int tasksCount = list.size();
        BlockingQueue<FutureTask> resolvedTasks = new LinkedBlockingQueue<>();
        final Collection<FutureTask<R, T>> futureTasks = this.createTasks(function, list,resolvedTasks);
        this.continueProcess();
        int resolvedTasksCount = 0;
        while (resolvedTasksCount++ < tasksCount) {
            this.wakeUpInterupt();
            FutureTask<R, T> task = resolvedTasks.take();

        }

        ArrayList<R> result = new ArrayList<R>();
        futureTasks.forEach(task -> result.add(task.getResult()));

        return result;
    }


}
