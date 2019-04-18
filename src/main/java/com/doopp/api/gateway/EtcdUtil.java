package com.doopp.api.gateway;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import mousio.client.retry.RetryOnce;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.promises.EtcdResponsePromise;
import mousio.etcd4j.responses.EtcdAuthenticationException;
import mousio.etcd4j.responses.EtcdException;
import mousio.etcd4j.responses.EtcdKeysResponse;

public class EtcdUtil {

    private EtcdClient client;

    // 自定义的服务名字
    private String serverName = "zzzz";

    // 自定义的一个标识
    private String zoneId = "1";

    private final String dirString = "/roomServerList";

    // 这里就是发布的节点
    private final String etcdKey = dirString + "/" + zoneId + "/" + serverName;

    //
    public EtcdUtil() {
        URI[] uris = new URI[1];
        uris[0] = URI.create("http://127.0.0.1:2379");
        client = new EtcdClient(uris);
        client.setRetryHandler(new RetryOnce(20)); //retry策略
    }

    // 注册节点，放在程序启动的入口
    public void regist() {
        try { // 用put方法发布一个节点
            EtcdResponsePromise<EtcdKeysResponse> p = client
                .putDir(etcdKey + "_" + host + "_" + port)
                .ttl(60).send();
            p.get(); // 加上这个get()用来保证设置完成，走下一步，get会阻塞，由上面client的retry策略决定阻塞的方式
            new Thread(new EtcdServiceRefresh()).start(); // 启动一个守护线程来定时刷新节点
        } catch (Exception e) {
            // TODO: handle exception
            System.out.println("etcd Server not available.");
        }
    }

    public void destory() {
        try {
            EtcdResponsePromise<EtcdKeysResponse> p = client
                .deleteDir(etcdKey + "_" + host + "_" + port)
                .recursive().send();
            p.get();
            client.close();
        } catch (IOException | EtcdException | EtcdAuthenticationException | TimeoutException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public class EtcdServiceRefresh implements Runnable {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            while (true) {
                try {
                    Thread.sleep(40*1000l);
                    client.refresh(etcdKey + "_" + host + "_" + port, 60).send();
                } catch (IOException | InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }
}
