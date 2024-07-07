package org.example;

import lombok.AllArgsConstructor;

import java.util.*;
import java.util.concurrent.*;

import static java.lang.String.join;


public class MultiThreadCrawler {

    public static void main(String[] args) throws Exception {
        MultiThreadCrawler crawler = new MultiThreadCrawler();

        long startTime = System.nanoTime();
        String result = crawler.find("Injury", "Equator", 5, TimeUnit.MINUTES);
        long finishTime = TimeUnit.SECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);

        System.out.println("Took "+finishTime+" seconds, result is: " + result);
    }

    private Queue<Node> searchQueue = new ConcurrentLinkedQueue<>();

    private Set<String> visited = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private ExecutorService executorService = Executors.newFixedThreadPool(100);

    private WikiClient client = new WikiClient();

    public String find(String from, String target, long timeout, TimeUnit timeUnit) throws Exception {
        long deadline = System.nanoTime() + timeUnit.toNanos(timeout);
        searchQueue.offer(new Node(from, null));
        while (!searchQueue.isEmpty()) {
            if (deadline < System.nanoTime()) {
                throw new TimeoutException();
            }

            var futures = new ArrayList<Future<Optional<Node>>>();
            searchQueue.forEach(node -> futures.add(executorService.submit(() -> processLink(node, target))));

            for (Future<Optional<Node>> f : futures) {
                try {
                    var result = f.get();
                    if (result.isPresent()) {
                        executorService.shutdownNow();
                        return extractResult(result.get());
                    }
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }

        }

        return "not found";
    }

    private String extractResult(Node result) {
        List<String> resultList = new ArrayList<>();
        Node search = result;
        while (true) {
            resultList.add(search.title);
            if (search.next == null) {
                break;
            }
            search = search.next;
        }
        Collections.reverse(resultList);

        return join(" > ", resultList);
    }

    private Optional<Node> processLink(Node current, String target) throws Exception {
        System.out.println("Get page: " + current.title);
        Set<String> links = client.getByTitle(current.title);
        for (String link : links) {
            String currentLink = link.toLowerCase();
            if (visited.add(currentLink)) {
                Node subNode = new Node(link, current);
                if (target.equalsIgnoreCase(currentLink)) {
                    return Optional.of(subNode);
                }
                searchQueue.offer(subNode);
            }
        }
        return Optional.empty();
    }

    @AllArgsConstructor
    private static class Node {
        String title;
        Node next;
    }

}
