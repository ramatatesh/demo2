package com.example.demo2.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LoadBalancerService {

    private static final Logger logger =
            LoggerFactory.getLogger(LoadBalancerService.class);


    private static final String[] servers = {
            "http://localhost:8080",
            "http://localhost:8081",
            "http://localhost:8082"
    };

    private final RestTemplate restTemplate = new RestTemplate();

    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    private final AtomicInteger totalRequests     = new AtomicInteger(0);

    private final AtomicInteger[] serverCounts = {
            new AtomicInteger(0),
            new AtomicInteger(0),
            new AtomicInteger(0)
    };

    public String handleWithoutLoadBalancing() {
        int taskNumber = totalRequests.incrementAndGet();
        serverCounts[0].incrementAndGet();

        String targetServer = servers[0];
        logger.info("Task {} -> Handled by node on port 8080", taskNumber);

        try {
            String response = restTemplate.getForObject(
                    targetServer + "/products", String.class
            );
            return "Task " + taskNumber +
                    " -> Handled by node on port 8080" +
                    " | Real Response received: YES";
        } catch (Exception e) {
            return "Task " + taskNumber +
                    " -> Handled by node on port 8080" +
                    " | Error: " + e.getMessage();
        }
    }

    private boolean isServerAlive(String serverUrl) {
        try {
            restTemplate.getForObject(serverUrl + "/products/instance", String.class);
            return true;
        } catch (Exception e) {
            logger.warn("Health Check Failed for server: {}", serverUrl);
            return false;
        }
    }


    public String handleWithLoadBalancing() {
        int taskNumber = totalRequests.incrementAndGet();
        int index = 0;
        String targetServer = "";
        int port = 8080;
        boolean foundAliveServer = false;
        for (int i = 0; i < servers.length; i++) {
            index = roundRobinCounter.getAndIncrement() % servers.length;
            targetServer = servers[index];
            port = 8080 + index;

            if (isServerAlive(targetServer)) {
                foundAliveServer = true;
                break;
            }
            logger.info("Task {} -> Server on port {} is DOWN, trying next...", taskNumber, port);
        }
        if (!foundAliveServer) {
            return "Task " + taskNumber + " -> All servers are DOWN! | Real Response received: NO";
        }
        serverCounts[index].incrementAndGet();
        logger.info("Task {} -> Handled by HEALTHY node on port {}", taskNumber, port);
        try {
            String response = restTemplate.getForObject(targetServer + "/products", String.class);
            return "Task " + taskNumber +
                    " -> Handled by node on port " + port +
                    " | Real Response received: YES";
        } catch (Exception e) {
            return "Task " + taskNumber +
                    " -> Handled by node on port " + port +
                    " | Error: " + e.getMessage();
        }
    }

    //  إحصائيات
    public List<ServerStats> getStats() {
        List<ServerStats> stats = new ArrayList<>();
        for (int i = 0; i < servers.length; i++) {
            stats.add(new ServerStats(
                    "port " + (8080 + i),
                    serverCounts[i].get(),
                    0
            ));
        }
        return stats;
    }

    public void resetAll() {
        roundRobinCounter.set(0);
        totalRequests.set(0);
        for (AtomicInteger c : serverCounts) c.set(0);
        logger.info("Reset completed");
    }

    public int getTotalRequests() {
        return totalRequests.get();
    }

    public static class ServerStats {
        public String serverName;
        public int requestsHandled;
        public double avgProcessingTimeMs;

        public ServerStats(String name, int requests, double avgTime) {
            this.serverName          = name;
            this.requestsHandled     = requests;
            this.avgProcessingTimeMs = avgTime;
        }
    }
}