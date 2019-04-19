package com.doopp.gateway;

import mousio.client.retry.RetryOnce;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.promises.EtcdResponsePromise;
import mousio.etcd4j.responses.EtcdAuthenticationException;
import mousio.etcd4j.responses.EtcdException;
import mousio.etcd4j.responses.EtcdKeysResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

public class GatewayEtcdUtil implements Runnable {

    private EtcdClient client;

    private Map<String, String> serviceBaseUrlMap;

    GatewayEtcdUtil(Map<String, String> serviceBaseUrlMap, Properties properties) {
        this.serviceBaseUrlMap = serviceBaseUrlMap;
        String[] etcdServerList = properties.getProperty("etcd.server.list").split(",");
        URI[] uris = new URI[etcdServerList.length];
        for(int ii=0; ii<etcdServerList.length; ii++) {
            uris[ii] = URI.create(etcdServerList[ii]);
        }
        client = new EtcdClient(uris);
        // retry 策略
        client.setRetryHandler(new RetryOnce(20));
        this.regist();
    }

    private void regist() { // 注册节点，放在程序启动的入口
        try {
            // 用put方法发布一个节点
            EtcdResponsePromise<EtcdKeysResponse> p = client.put("key", "value").ttl(60).send();
            // get 会阻塞，由上面client的retry策略决定阻塞的方式, 用来保证设置完成
            p.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void destory() {
        try {
            EtcdResponsePromise<EtcdKeysResponse> p = client.delete("key").recursive().send();
            p.get();
            client.close();
        } catch (IOException | EtcdException | EtcdAuthenticationException | TimeoutException e) {
            // TODO Auto-generated catch block
            // e.printStackTrace();
        }
    }

    @Override
    public synchronized void run() {
        try {
            EtcdResponsePromise<EtcdKeysResponse> etcdResponsePromise = client.getAll().recursive().send();
            etcdResponsePromise.addListener(promise -> {
                System.out.println(promise);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        int ii=0;
        while(true) {
            try {
                Thread.sleep(618);
                ii ++;
                client.put("key" + ii, "value" + ii).ttl(60).send();
                //System.out.println(etcdResponsePromise);
                //this.serviceBaseUrlMap.put("", "");
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
    }
}
