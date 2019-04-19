package com.doopp.api.gateway;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

import javax.annotation.PreDestroy;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class GatewayServer {

    private static Map<String, String> serviceBaseUrlMap = new HashMap<>();

    private static GatewayEtcdUtil gatewayEtcdUtil;

    public static void main(String[] args) throws IOException {

        Properties properties = new Properties();
        properties.load(new FileInputStream(args[0]));

        // 抛出线程监听 etcd ，并刷新 Api Gateway 的路由表
        gatewayEtcdUtil = new GatewayEtcdUtil(serviceBaseUrlMap, properties);
        new Thread(gatewayEtcdUtil).start();

        // 运行 Api Gateway
        GatewayServer gatewayServer = new GatewayServer();
        gatewayServer.launch();
    }

    private static HttpClient httpClient = HttpClient.create();

    /**
     * 从 uri ( /api/v1/user/1 ) 判断请求的微服务地址
     *
     * @param uri request Uri
     * @return String
     */
    private String getServiceBaseUrl(String uri) {
        String key = uri.replaceAll("^(/[^/]+/[^/]+/[^/]+)/.*$", "$1");
        return serviceBaseUrlMap.get(key);
    }

    private void launch() {

        // 启动 API Gateway Server
        DisposableServer disposableServer = HttpServer.create()
                .tcpConfiguration(tcpServer ->
                        tcpServer.option(ChannelOption.SO_KEEPALIVE, true)
                )
                .handle((req, resp) -> {

                    String serviceUri = req.uri();
                    String serviceBaseUrl = getServiceBaseUrl(serviceUri);

                    if (serviceBaseUrl==null) {
                        return resp.sendNotFound();
                    }

                    HttpClient httpClientRequest = httpClient
                            .baseUrl(serviceBaseUrl)
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
                        responseReceiver = httpClientRequest
                                .post()
                                .uri(serviceUri)
                                .send(req.receiveContent().ofType(ByteBuf.class));
                    }
                    // PUT
                    else if (req.method().equals(HttpMethod.PUT)) {
                        responseReceiver = httpClientRequest
                                .put()
                                .uri(serviceUri)
                                .send(req.receiveContent().ofType(ByteBuf.class));
                    }
                    // DELETE
                    else if (req.method().equals(HttpMethod.DELETE)) {
                        responseReceiver = httpClientRequest
                                .delete()
                                .uri(serviceUri);
                    }
                    // GET
                    else {
                        responseReceiver = httpClientRequest
                                .get()
                                .uri(serviceUri);
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

    @PreDestroy
    public void destory() {
        gatewayEtcdUtil.destory();
    }
}
