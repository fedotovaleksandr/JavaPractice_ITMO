package ru.ifmo.ctddev.fedotov.mapper;

import java.util.*;
import java.util.function.Function;

/**
 * Created by aleksandr on 22.12.16.
 */
public class ThreadPoolExecutor {
    private volatile Queue<FutureTask> tasks = new ArrayDeque<>();
    private final int threadsCount;
    private List<Thread> threads = new ArrayList<>();
    final  Object monitor = new Object();

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
        return futureTasks;
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
        this.wakeThreads();
        synchronized (this.monitor){
            this.monitor.notifyAll();
        }
    }
    private void wakeThreads(){
        for (Thread th:this.threads){
            if (th.getState() == Thread.State.NEW){
                th.start();
            }
        }
    }

    public boolean isComplete() {
        boolean answer = true;

        for (int i = 0 ;i< this.threads.size();i++) {
            Thread th = this.threads.get(i);
            Thread.State threadState = th.getState();
            if (threadState == Thread.State.TERMINATED){
                th.interrupt();
                Thread newthread = new Thread(new FutureRunnable(this.tasks, monitor), "Thread" + i);
                this.threads.set(i,newthread);
                newthread.start();
            }
            answer &= th.getState() == Thread.State.WAITING;
        }
        return answer;
    }
}
