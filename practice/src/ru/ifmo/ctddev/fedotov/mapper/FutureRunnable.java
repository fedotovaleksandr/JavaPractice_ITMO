package ru.ifmo.ctddev.fedotov.mapper;

import java.util.Queue;

/**
 * Created by aleksandr on 22.12.16.
 */
public class FutureRunnable implements Runnable {


    private Queue<FutureTask> tasks;
    private final Object monitor;

    public FutureRunnable(Queue<FutureTask> tasks, Object monitor) {
        this.tasks = tasks;
        this.monitor = monitor;
    }

    @Override
    public void run() {

        while (!Thread.currentThread().isInterrupted()) {
            try {
                FutureTask task = this.getTask();
                if (task == null) {
                    synchronized (monitor) {
                        this.monitor.wait();
                    }
                } else {
                    task.doWork();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // propagate interrupt
                break;
            }
        }

    }

    private synchronized FutureTask getTask() {
        return this.tasks.poll();
    }

}
