/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.webserver.netty;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.net.ssl.SSLEngine;

import io.helidon.webserver.Routing;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;

/**
 * The HttpInitializer.
 */
class HttpInitializer extends ChannelInitializer<SocketChannel> {

    private final SslContext sslContext;
    private final NettyWebServer webServer;
    private final Routing routing;
    private final Queue<ReferenceHoldingQueue<ByteBufRequestChunk>> queues = new ConcurrentLinkedQueue<>();

    HttpInitializer(SslContext sslContext, Routing routing, NettyWebServer webServer) {
        this.routing = routing;
        this.sslContext = sslContext;
        this.webServer = webServer;
    }

    private void clearQueues() {
        queues.removeIf(ReferenceHoldingQueue::release);
    }

    void queuesShutdown() {
        queues.removeIf(queue -> {
            queue.shutdown();
            return true;
        });
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();

        SSLEngine sslEngine = null;
        if (sslContext != null) {
            SslHandler sslHandler = sslContext.newHandler(ch.alloc());
            sslEngine = sslHandler.engine();
            p.addLast(sslHandler);
        }

        p.addLast(new HttpRequestDecoder());
        // Uncomment the following line if you don't want to handle HttpChunks.
        //        p.addLast(new HttpObjectAggregator(1048576));
        p.addLast(new HttpResponseEncoder());
        // Remove the following line if you don't want automatic content compression.
        //p.addLast(new HttpContentCompressor());
        p.addLast(new ForwardingHandler(routing, webServer, sslEngine, queues));

        ch.eventLoop().execute(this::clearQueues);
    }
}
