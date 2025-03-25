package com.neel.redis.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neel.redis.service.CacheService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.EventExecutorGroup;

import java.util.concurrent.TimeUnit;

class ServerInitializer extends ChannelInitializer<SocketChannel> {
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final EventExecutorGroup businessGroup;

    public ServerInitializer(CacheService cacheService, ObjectMapper objectMapper, EventExecutorGroup businessGroup) {
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.businessGroup = businessGroup;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        // Connection timeout handler - close inactive connections
        pipeline.addLast(new IdleStateHandler(0, 0, 30, TimeUnit.SECONDS));

        // HTTP codec - efficient HTTP parsing
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new HttpObjectAggregator(65536));

        // Put business logic handler in separate thread pool to prevent I/O thread blocking
        pipeline.addLast(businessGroup, "cacheHandler", new CacheHttpHandler(cacheService, objectMapper));
    }
}