package com.doopp.api.gateway;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

import java.io.IOException;

public class GatewayServer {

    public static void main(String[] args) {

        HttpClient httpClient = HttpClient.create();

        // 抛出线程监听 etcd ，并刷新 Api Gateway 的路由表
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(40*1000);
                    client.refresh(etcdKey + "_" + host + "_" + port, 60).send();
                } catch (IOException | InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }).start();

        // 启动 API Gateway Server
        DisposableServer disposableServer = HttpServer.create()
                .tcpConfiguration(tcpServer ->
                        tcpServer.option(ChannelOption.SO_KEEPALIVE, true)
                )
                .handle((req, resp) -> {

                    HttpClient httpClientRequest = httpClient
                            .baseUrl("https://www.doopp.com")
                            .headers(entries -> {
                                HttpHeaders httpHeaders = req.requestHeaders();
                                entries.set("User-Agent", httpHeaders.get("User-Agent"));
                                // for(String name : httpHeaders.names()) {
                                //    System.out.printf("%s: %s\n", name, httpHeaders.get(name));
                                //    entries.set(name, httpHeaders.get(name));
                                // }
                            });

                    HttpClient.ResponseReceiver<?> responseReceiver;

                    // POST
                    if (req.method().equals(HttpMethod.POST)) {
                        responseReceiver = httpClientRequest.post()
                                .send(req.receiveContent().ofType(ByteBuf.class));
                    }
                    // PUT
                    else if (req.method().equals(HttpMethod.PUT)) {
                        responseReceiver = httpClientRequest.put()
                                .send(req.receiveContent().ofType(ByteBuf.class));
                    }
                    // DELETE
                    else if (req.method().equals(HttpMethod.DELETE)) {
                        responseReceiver = httpClientRequest.delete();
                    }
                    // GET
                    else {
                        responseReceiver = httpClientRequest.get().uri("/phpinfo.php");
                    }

                    return responseReceiver.response((httpClientResponse, byteBufFlux)->{
                                resp.headers(httpClientResponse.responseHeaders());
                                return byteBufFlux;
                            })
                            .flatMap(byteBuf -> {
                                // System.out.println(byteBuf.toString(Charset.forName("UTF-8")));
                                return resp.sendObject(byteBuf.retain());
                            });
                })
                .host("127.0.0.1")
                .port(8087)
                .wiretap(true)
                .bindNow();

        System.out.println("\n>>> Api Gateway Server Running ...\n");

        disposableServer.onDispose().block();
    }
}
