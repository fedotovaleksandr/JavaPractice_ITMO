package ru.ifmo.ctddev.fedotov.crawler;

/**
 * Created by aleksandr on 24.12.2016.
 */

import info.kgeorgiy.java.advanced.crawler.*;
import net.java.quickcheck.collection.Pair;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;

import static info.kgeorgiy.java.advanced.crawler.URLUtils.getHost;

public class WebCrawler implements Crawler {
    private final Downloader downloader;
    private final ExecutorService extractThreadPool;
    private final ExecutorService downloadThreadPool;
    private final Collection<String> downloadedUrlHashSet = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Integer> perHostDownloadsCount = new ConcurrentHashMap<>();
    private final Map<String, BlockingQueue<DownloadCallable>> downloadLeft = new ConcurrentHashMap<>();
    private final BlockingQueue<Pair<Future<String>, String>> processingQueue = new LinkedBlockingQueue<>();
    private final int perHost;

    public WebCrawler(
            final Downloader downloader,
            final int downloaders,
            final int extractors,
            final int perHost) {
        this.downloader = downloader;
        this.perHost = perHost;
        downloadThreadPool = Executors.newFixedThreadPool(downloaders);
        extractThreadPool = Executors.newFixedThreadPool(extractors);
    }

    @Override
    public Result download(final String url, final int depth) {
        try {
            return process(url, depth);
        } catch (InterruptedException ignored) {
            return new Result(Collections.emptyList(), Collections.emptyMap());
        }
    }

    @Override
    public void close() {
        downloadThreadPool.shutdown();
        extractThreadPool.shutdown();
        try {
            downloadThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            extractThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException ignored) {
        }
    }

    public static void main(final String[] args) {
        if (args.length < 1 || args.length > 4) {
            System.out.println("use: WebCrawler downloadableUrl [downloaders [extractors [perHost]]]");
        } else {
            final int downloaders = args.length > 1
                    ? Integer.parseInt(args[1])
                    : 1;
            final int extractors = args.length > 2
                    ? Integer.parseInt(args[2])
                    : 1;
            final int perHost = args.length > 3
                    ? Integer.parseInt(args[3])
                    : 1;

            try (Crawler crawler = new WebCrawler(new CachingDownloader(), downloaders, extractors, perHost)) {
                System.out.println(crawler.download(args[0], 3));
            } catch (IOException e) {
                System.err.println("Can't download page: " + e.getMessage());
            }
        }
    }


    private Result process(final String url, final int depth) throws InterruptedException {
        try {
            perHostDownloadsCount.put(getHost(url), 1);
            processingQueue.add(
                    new Pair<Future<String>, String>(
                            downloadThreadPool.submit(new DownloadCallable(url, 1, depth)),
                            url)
            );

            final List<String> result = new ArrayList<>();
            final Map<String, IOException> errors = new HashMap<>();

            while (!processingQueue.isEmpty()) {
                final Pair<Future<String>, String> pair = processingQueue.take();
                final Future<String> future = pair.getFirst();
                try {
                    final String res = future.get();

                    if (res != null && !errors.containsKey(res)) {
                        result.add(res);
                    }

                } catch (ExecutionException e) {
                    final Throwable cause = e.getCause();
                    errors.put(pair.getSecond(), (IOException) cause);
                    continue;
                }
            }
            return new Result(result, errors);
        } catch (
                MalformedURLException e)

        {
            return new Result(Collections.emptyList(), Collections.singletonMap(url, e));
        }
    }

    private class ExtractionCallable implements Callable<String> {
        private final int depth;
        private final int maxDepth;
        private final Document document;

        ExtractionCallable(
                final Document document,
                final int depth,
                final int maxDepth) {
            this.depth = depth;
            this.maxDepth = maxDepth;
            this.document = document;
        }

        @Override
        public String call() throws IOException, InterruptedException {
            final List<String> links = document.extractLinks();
            for (String link : links) {
                final DownloadCallable downloadTask = new DownloadCallable(link, depth + 1, maxDepth);
                final Future<String> downloadFuture = downloadThreadPool.submit(downloadTask);
                processingQueue.add(new Pair<>(downloadFuture, link));
            }
            return null;
        }
    }

    private class DownloadCallable implements Callable<String> {
        private final String url;
        private final int depth;
        private final int maxDepth;

        DownloadCallable(
                final String url,
                final int depth,
                final int maxDepth) {
            this.url = url;
            this.depth = depth;
            this.maxDepth = maxDepth;
        }

        @Override
        public String call() throws IOException, InterruptedException {
            if (downloadedUrlHashSet.add(url)) {
                final Document document = downloader.download(url);
                if (depth < maxDepth) {
                    final Callable<String> extractionTask = new ExtractionCallable(document, depth, maxDepth);
                    final Future<String> extractionFuture = extractThreadPool.submit(extractionTask);
                    processingQueue.put(new Pair<>(extractionFuture, url));
                }
            }
            return url;
        }
    }
}
