package org.digitalforge.monobuild.helper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Singleton;

@Singleton
public class ThreadHelper {

    public ExecutorService newThreadPool(String name, int threads) {
        ExecutorService executorService = Executors.newFixedThreadPool(threads, newThreadFactory(name));
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(threads, threads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                newThreadFactory(name)
        );
        Runtime.getRuntime().addShutdownHook(new Thread(executorService::shutdown));
        return executorService;
    }

    public ThreadFactory newThreadFactory(String name) {
        return new MyThreadFactory(name);
    }

    private static final class MyThreadFactory implements ThreadFactory {

        private static final AtomicInteger pool = new AtomicInteger(1);

        private final ThreadGroup group;
        private final String prefix;
        private final AtomicInteger worker;

        public MyThreadFactory(String name) {
            this.group = Thread.currentThread().getThreadGroup();
            this.prefix = String.format("mono-%s-%s-", pool.getAndIncrement(), name);
            this.worker = new AtomicInteger(1);
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread t = new Thread(group, runnable, prefix + worker.getAndIncrement());
            t.setDaemon(false);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }

    }

}
