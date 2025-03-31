package com.neel.redis.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neel.redis.service.CacheService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class NettyServer implements CommandLineRunner {
    private static final int PORT = 8181;

    private static final int BOSS_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final int WORKER_THREADS = Runtime.getRuntime().availableProcessors() * 2;
    private static final int BUSINESS_LOGIC_THREADS = Runtime.getRuntime().availableProcessors() * 4;

    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    public NettyServer(CacheService cacheService, ObjectMapper objectMapper) {
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        final boolean useEpoll = System.getProperty("os.name").toLowerCase().contains("linux");

        EventLoopGroup bossGroup;
        EventLoopGroup workerGroup;
        Class<? extends ServerChannel> channelClass;

        if (useEpoll) {
            bossGroup = new EpollEventLoopGroup(BOSS_THREADS);
            workerGroup = new EpollEventLoopGroup(WORKER_THREADS);
            channelClass = EpollServerSocketChannel.class;
        } else {
            bossGroup = new NioEventLoopGroup(BOSS_THREADS);
            workerGroup = new NioEventLoopGroup(WORKER_THREADS);
            channelClass = NioServerSocketChannel.class;
        }

        final EventExecutorGroup businessGroup = new DefaultEventExecutorGroup(BUSINESS_LOGIC_THREADS);

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(channelClass)
             .option(ChannelOption.SO_BACKLOG, 16384)
             .option(ChannelOption.SO_REUSEADDR, true)
             .childOption(ChannelOption.SO_KEEPALIVE, true)
             .childOption(ChannelOption.TCP_NODELAY, true)
             .childOption(ChannelOption.SO_RCVBUF, 131072)
             .childOption(ChannelOption.SO_SNDBUF, 131072)
             .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new ServerInitializer(cacheService, objectMapper, businessGroup));

            ChannelFuture f = b.bind(PORT).sync();
            System.out.println("Netty server started on port " + PORT);

            f.channel().closeFuture().sync();
        } finally {
            businessGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}