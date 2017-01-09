package ru.ifmo.ctddev.fedotov.mapper;

import java.util.concurrent.BlockingQueue;

/**
 * Created by aleksandr on 22.12.16.
 */
public class FutureRunnable implements Runnable {


    private BlockingQueue<FutureTask> tasks;


    public FutureRunnable(BlockingQueue<FutureTask> tasks) {
        this.tasks = tasks;
    }


    @Override
    public void run() {

        while (!Thread.currentThread().isInterrupted()) {
            try {
                //wait on get task because block queue
                FutureTask task = this.tasks.take();
                task.doWork();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // propagate interrupt
                break;
            }
        }

    }
}
