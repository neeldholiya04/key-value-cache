package com.neel.redis.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neel.redis.service.CacheService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.util.List;
import java.util.Map;

class CacheHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    public CacheHttpHandler(CacheService cacheService, ObjectMapper objectMapper) {
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String uri = request.uri();

        if (uri.startsWith("/get") && request.method() == HttpMethod.GET) {
            QueryStringDecoder decoder = new QueryStringDecoder(uri);
            Map<String, List<String>> params = decoder.parameters();

            if (!params.containsKey("key") || params.get("key").isEmpty()) {
                sendHttpResponse(ctx, request, Map.of(
                    "status", "ERROR",
                    "message", "Missing 'key' parameter"
                ));
                return;
            }

            String key = params.get("key").get(0);
            Map<String, Object> response = cacheService.get(key);
            sendHttpResponse(ctx, request, response);
        } else if (uri.equals("/put") && request.method() == HttpMethod.POST) {
            try {
                ByteBuf content = request.content();
                if (content.readableBytes() == 0) {
                    sendHttpResponse(ctx, request, Map.of(
                        "status", "ERROR",
                        "message", "Empty request body"
                    ));
                    return;
                }

                String jsonStr = content.toString(CharsetUtil.UTF_8);
                Map<String, String> requestMap = objectMapper.readValue(jsonStr, Map.class);

                if (!requestMap.containsKey("key") || !requestMap.containsKey("value")) {
                    sendHttpResponse(ctx, request, Map.of(
                        "status", "ERROR",
                        "message", "Missing 'key' or 'value' in request"
                    ));
                    return;
                }

                Map<String, Object> response = cacheService.put(requestMap.get("key"), requestMap.get("value"));
                sendHttpResponse(ctx, request, response);
            } catch (Exception e) {
                sendHttpResponse(ctx, request, Map.of(
                    "status", "ERROR",
                    "message", "Invalid request format: " + e.getMessage()
                ));
            }
        } else {
            sendHttpResponse(ctx, request, Map.of(
                "status", "ERROR",
                "message", "Unsupported endpoint or method"
            ));
        }
    }

    private void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, Map<String, Object> responseMap) throws Exception {
        String jsonResponse = objectMapper.writeValueAsString(responseMap);

        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            Unpooled.copiedBuffer(jsonResponse, CharsetUtil.UTF_8));

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

        boolean keepAlive = HttpUtil.isKeepAlive(req);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }

        ChannelFuture future = ctx.writeAndFlush(response);
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}