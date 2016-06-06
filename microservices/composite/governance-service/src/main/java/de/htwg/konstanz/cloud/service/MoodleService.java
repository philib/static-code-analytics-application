package de.htwg.konstanz.cloud.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.concurrent.Future;


@Service
public class MoodleService {
    private static final Logger LOG = LoggerFactory.getLogger(MoodleService.class);


    @Autowired
    private LoadBalancerClient loadBalancer;

    @Autowired
    private GovernanceUtil util;


    @Async
    public String getCourses(String token) throws InstantiationException {
        String route = "/courses/token/" + token;

        return util.getFromService(route, "moodle");
    }

    public String getUserInformation(String token) {
        return null;
    }
}
