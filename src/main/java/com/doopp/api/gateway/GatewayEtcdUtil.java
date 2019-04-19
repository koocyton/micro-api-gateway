package com.doopp.api.gateway;

import mousio.client.retry.RetryOnce;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.requests.EtcdKeyGetRequest;

import java.net.URI;

public class GatewayEtcdUtil implements Runnable {

    private EtcdClient client;

    GatewayEtcdUtil() {
        URI[] uris = new URI[1];
        uris[0] = URI.create("http://127.0.0.1:2379");
        client = new EtcdClient(uris);
        client.setRetryHandler(new RetryOnce(20)); //retry策略
    }

    @Override
    public void run() {

        while(true) {
            try {
                Thread.sleep(40 * 1000L);
                EtcdKeyGetRequest etcdKeyGetRequest = client.getAll();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
