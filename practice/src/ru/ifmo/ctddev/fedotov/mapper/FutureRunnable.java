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

        while (true) {
            FutureTask task = this.getTask();
            if (task == null) {
                try {
                    synchronized (monitor){
                        this.monitor.wait(1);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                task.doWork();
            }
        }

    }

    private synchronized FutureTask getTask() {
        return this.tasks.poll();
    }

}
