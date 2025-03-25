//package com.neel.redis.server;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.neel.redis.service.CacheService;
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.channel.SimpleChannelInboundHandler;
//import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
//import io.netty.handler.codec.http.websocketx.WebSocketFrame;
//
//import java.util.Map;
//
//class CacheWebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
//    private final CacheService cacheService;
//    private final ObjectMapper objectMapper;
//
//    public CacheWebSocketHandler(CacheService cacheService, ObjectMapper objectMapper) {
//        this.cacheService = cacheService;
//        this.objectMapper = objectMapper;
//    }
//
//    @Override
//    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
//        if (frame instanceof TextWebSocketFrame) {
//            String request = ((TextWebSocketFrame) frame).text();
//            Map<String, Object> requestMap = objectMapper.readValue(request, Map.class);
//
//            Map<String, Object> responseMap;
//            String operation = (String) requestMap.get("operation");
//            String key = (String) requestMap.get("key");
//
//            if ("get".equals(operation)) {
//                responseMap = cacheService.get(key);
//            } else if ("put".equals(operation)) {
//                String value = (String) requestMap.get("value");
//                responseMap = cacheService.put(key, value);
//            } else {
//                responseMap = Map.of("status", "ERROR", "message", "Unknown operation");
//            }
//
//            String response = objectMapper.writeValueAsString(responseMap);
//            ctx.channel().writeAndFlush(new TextWebSocketFrame(response));
//        } else {
//            String message = "Unsupported frame type: " + frame.getClass().getName();
//            throw new UnsupportedOperationException(message);
//        }
//    }
//
//    @Override
//    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
//        cause.printStackTrace();
//        ctx.close();
//    }
//}
