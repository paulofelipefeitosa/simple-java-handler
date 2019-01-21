package com.thumbnailator.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import com.sun.net.httpserver.Headers;
import com.thumbnailator.model.*;

public class App {

    public static TimestampExecutor tsExecutor;

    protected static class TimestampExecutor implements Executor {

        public long runTaskTimestamp;

        public void execute(Runnable task) {
            runTaskTimestamp = System.currentTimeMillis();
            task.run();
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 9000;

        IHandler handler = new com.thumbnailator.function.Handler();

        InetSocketAddress addr = new InetSocketAddress(port);
        HttpServer server = HttpServer.create(addr, 0);
        InvokeHandler invokeHandler = new InvokeHandler(handler);

        tsExecutor = new TimestampExecutor();

        server.createContext("/", invokeHandler);
        server.setExecutor(tsExecutor); // creates a default executor
        server.start();

        System.out.println("SERVER STARTED!");
        System.out.println("LISTENING TO:" + server.getAddress());
    }

    static class InvokeHandler implements HttpHandler {
        IHandler handler;

        private InvokeHandler(IHandler handler) {
            this.handler = handler;
        }

        @Override
        public void handle(HttpExchange t) throws IOException {
            long readyTime = System.currentTimeMillis();
            long jvmStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();
            String requestBody = "";
            String method = t.getRequestMethod();

            if (method.equalsIgnoreCase("POST")) {
                InputStream inputStream = t.getRequestBody();
                ByteArrayOutputStream result = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    result.write(buffer, 0, length);
                }
                // StandardCharsets.UTF_8.name() > JDK 7
                requestBody = result.toString("UTF-8");
            }

            // System.out.println(requestBody);
            Headers reqHeaders = t.getRequestHeaders();
            Map<String, String> reqHeadersMap = new HashMap<String, String>();

            for (Map.Entry<String, java.util.List<String>> header : reqHeaders.entrySet()) {
                java.util.List<String> headerValues = header.getValue();
                if (headerValues.size() > 0) {
                    reqHeadersMap.put(header.getKey(), headerValues.get(0));
                }
            }

            // for(Map.Entry<String, String> entry : reqHeadersMap.entrySet()) {
            // System.out.println("Req header " + entry.getKey() + " " + entry.getValue());
            // }

            IRequest req = new Request(requestBody, reqHeadersMap, t.getRequestURI().getRawQuery(),
                    t.getRequestURI().getPath());

            IResponse res = this.handler.Handle(req);

            String response = res.getBody();
            byte[] bytesOut = response.getBytes("UTF-8");

            Headers responseHeaders = t.getResponseHeaders();
            String contentType = res.getContentType();
            if (contentType.length() > 0) {
                responseHeaders.set("Content-Type", contentType);
            }

            for (Map.Entry<String, String> entry : res.getHeaders().entrySet()) {
                responseHeaders.set(entry.getKey(), entry.getValue());
            }
            responseHeaders.add("X-Run-Handler-Task-Timestamp",
                    Long.toString(tsExecutor.runTaskTimestamp));
            responseHeaders.add("X-JVM-Startup-Timestamp", Long.toString(jvmStartTime));
            responseHeaders.add("X-Ready-Timestamp", Long.toString(readyTime));

            t.sendResponseHeaders(res.getStatusCode(), bytesOut.length);

            OutputStream os = t.getResponseBody();
            os.write(bytesOut);
            os.close();

            // System.out.println("Request / " + Integer.toString(bytesOut.length) +" bytes
            // written.");
        }
    }

}
