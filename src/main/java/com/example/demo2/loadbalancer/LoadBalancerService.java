package com.example.demo2.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LoadBalancerService {

    private static final Logger logger =
            LoggerFactory.getLogger(LoadBalancerService.class);


    private final List<VirtualServer> servers = new ArrayList<>();

    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    private final AtomicInteger totalRequests = new AtomicInteger(0);

    public LoadBalancerService() {
        servers.add(new VirtualServer("Server-1"));
        servers.add(new VirtualServer("Server-2"));
        servers.add(new VirtualServer("Server-3"));

        logger.info(" Load Balancer initialized with {} virtual servers",
                servers.size());
    }


    public String handleWithoutLoadBalancing(int processingTimeMs) {

        totalRequests.incrementAndGet();
        VirtualServer singleServer = servers.get(0);

        logger.info("[NO Load Balancing] كل الطلبات تذهب إلى: {}",
                singleServer.getName());

        String result = singleServer.handleRequest(processingTimeMs);

        logger.info("[NO Load Balancing] نتيجة: {}", result);

        return result;
    }

    public String handleWithLoadBalancing(int processingTimeMs) {

        totalRequests.incrementAndGet();


        int serverIndex = roundRobinCounter.getAndIncrement() % servers.size();
        VirtualServer selectedServer = servers.get(serverIndex);

        logger.info(" [Load Balancer] وجّه الطلب إلى: {} (Index: {})",
                selectedServer.getName(), serverIndex);

        String result = selectedServer.handleRequest(processingTimeMs);

        logger.info(" [Load Balancer] نتيجة: {}", result);

        return result;
    }

    public List<ServerStats> getStats() {

        List<ServerStats> stats = new ArrayList<>();

        for (VirtualServer server : servers) {
            stats.add(new ServerStats(
                    server.getName(),
                    server.getRequestCount(),
                    server.getAverageProcessingTime()
            ));
        }

        return stats;
    }

    public void resetAll() {
        servers.forEach(VirtualServer::reset);
        roundRobinCounter.set(0);
        totalRequests.set(0);
        logger.info(" تم إعادة ضبط كل السيرفرات");
    }

    public int getTotalRequests() {
        return totalRequests.get();
    }

    public static class ServerStats {
        public String serverName;
        public int requestsHandled;
        public double avgProcessingTimeMs;

        public ServerStats(String name, int requests, double avgTime) {
            this.serverName = name;
            this.requestsHandled = requests;
            this.avgProcessingTimeMs = avgTime;
        }
    }
}
