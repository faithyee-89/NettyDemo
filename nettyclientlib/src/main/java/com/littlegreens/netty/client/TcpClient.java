package com.littlegreens.netty.client;

import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.littlegreens.netty.client.handler.NettyClientHandler;
import com.littlegreens.netty.client.listener.MessageStateListener;
import com.littlegreens.netty.client.listener.NettyClientListener;
import com.littlegreens.netty.client.status.ConnectState;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;

/**
 * Created by littleGreens on 2018-11-10.
 * TCP 客户端
 */
public class TcpClient {
    private static final String TAG = "TcpClient";
    private EventLoopGroup eventLoopGroupWorker;
    private final Bootstrap mBootstrap = new Bootstrap();
    private NettyClientListener nettyClientListener;
    private Channel channel;
    private boolean isConnect = false;
    private int MAX_CONNECT_TIMES = Integer.MAX_VALUE; //最大重连次数
    private int reconnectNum = MAX_CONNECT_TIMES;
    private boolean isNeedReconnect = true;
    private boolean isConnecting = false;
    private long reconnectIntervalTime = 5000;
    private static final Integer CONNECT_TIMEOUT_MILLIS = 5000;
    private String ipAddr;
    private int port;
    private int mIndex;
    private long heartBeatInterval = 5;//心跳间隔时间,单位秒
    private boolean isSendheartBeat = false;//是否发送心跳
    private Object heartBeatData;//心跳数据，可以是String类型，也可以是byte[].
    private String packetSeparator;
    private int maxPacketLong = 1024;

    private TcpClient(String ipAddr, int port, int index) {
        this.ipAddr = ipAddr;
        this.port = port;
        this.mIndex = index;
        initClient();
    }

    private void initClient() {
        eventLoopGroupWorker = new NioEventLoopGroup();
        mBootstrap
                .group(eventLoopGroupWorker)
                .option(ChannelOption.TCP_NODELAY, true)//屏蔽Nagle算法试图
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        if (isSendheartBeat) {
                            ch.pipeline().addLast("ping", new IdleStateHandler(0, heartBeatInterval, 0, TimeUnit.SECONDS));//5s未发送数据，回调userEventTriggered
                        }

                        //黏包处理,需要客户端、服务端配合
                        if (!TextUtils.isEmpty(packetSeparator)) {
                            ByteBuf delimiter = Unpooled.buffer();
                            delimiter.writeBytes(packetSeparator.getBytes());
                            ch.pipeline().addLast(new DelimiterBasedFrameDecoder(maxPacketLong, delimiter));
                        } else {
                            ch.pipeline().addLast(new LineBasedFrameDecoder(maxPacketLong));
                        }
                        ch.pipeline().addLast(new StringEncoder(CharsetUtil.UTF_8));
                        ch.pipeline().addLast(new StringDecoder(CharsetUtil.UTF_8));
                        ch.pipeline().addLast(new NettyClientHandler(nettyClientListener, mIndex, isSendheartBeat, heartBeatData, packetSeparator));
                    }
                });
    }

    public void connect(String ip, int port) {
        if (isConnecting) {
            return;
        }
        Thread clientThread = new Thread("client-Netty") {
            @Override
            public void run() {
                super.run();
                isNeedReconnect = true;
                reconnectNum = MAX_CONNECT_TIMES;
                connectServer(ip, port);
            }
        };
        clientThread.start();
    }

    private void connectServer(String ip, int port) {
        this.ipAddr = ip;
        this.port = port;
        synchronized (TcpClient.this) {
            ChannelFuture channelFuture = null;
            if (!isConnect) {
                isConnecting = true;
                try {

                    channelFuture = mBootstrap
                            .connect(ip, port).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture channelFuture) throws Exception {
                            if (channelFuture.isSuccess()) {
                                Log.e(TAG, "连接成功");
                                reconnectNum = MAX_CONNECT_TIMES;
                                isConnect = true;
                                channel = channelFuture.channel();
                            } else {
                                Log.e(TAG, "连接失败");
                                isConnect = false;
                            }
                            isConnecting = false;
                        }
                    }).sync();

                    // Wait until the connection is closed.
                    channelFuture.channel().closeFuture().sync();
                    Log.e(TAG, " 断开连接");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    isConnect = false;
                    if (nettyClientListener != null) {
                        nettyClientListener.onClientStatusConnectChanged(ConnectState.STATUS_CONNECT_CLOSED, mIndex);
                    }
                    if (channelFuture != null) {
                        if (channelFuture.channel() != null && channelFuture.channel().isOpen()) {
                            channelFuture.channel().close();
                        }
                    }
                    eventLoopGroupWorker.shutdownGracefully();
                    reconnect();
                }
            }
        }
    }

    public void disconnect() {
        Log.e(TAG, "disconnect");
        isNeedReconnect = false;
        this.eventLoopGroupWorker.shutdownGracefully();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void reconnect() {
        Log.e(TAG, "reconnect");
        if (isNeedReconnect && reconnectNum > 0 && !isConnect) {
            reconnectNum--;
            SystemClock.sleep(reconnectIntervalTime);
            if (isNeedReconnect && reconnectNum > 0 && !isConnect) {
                Log.e(TAG, "重新连接");
                connectServer(ipAddr, port);
            }
        }
    }


    /**
     * 异步发送
     *
     * @param msg      发送int数据
     * @param listener 发送状态回调
     * @return 是否已发送
     */
    public boolean sendMsgToServer(int[] msg, final MessageStateListener listener) {
        boolean flag = channel != null && isConnect;
        if (flag) {
            ByteBuf sendData = ByteBufAllocator.DEFAULT.buffer();
            for (int i = 0; i < msg.length; i++) {
                sendData.writeByte(msg[i]);
            }

            channel.writeAndFlush(
                    sendData
            ).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    listener.isSendSuccss(channelFuture.isSuccess());
                }
            });
        }
        return flag;
    }

    /**
     * 同步发送
     *
     * @param data 要发送的数据
     * @return 方法执行结果
     */
    public boolean sendMsgToServer(String data) {
        boolean flag = channel != null && isConnect;
        if (flag) {
            String separator = TextUtils.isEmpty(packetSeparator) ? System.getProperty("line.separator") : packetSeparator;
            ChannelFuture channelFuture = channel.writeAndFlush(data + separator).awaitUninterruptibly();
            return channelFuture.isSuccess();
        }
        return false;
    }

    private void setPacketSeparator(String separator) {
        this.packetSeparator = separator;
    }

    private void setMaxPacketLong(int maxPacketLong) {
        this.maxPacketLong = maxPacketLong;
    }

    public int getMaxConnectTimes() {
        return MAX_CONNECT_TIMES;
    }

    public long getReconnectIntervalTime() {
        return reconnectIntervalTime;
    }

    public String getIpAddr() {
        return ipAddr;
    }

    public int getPort() {
        return port;
    }

    public int getIndex() {
        return mIndex;
    }

    public long getHeartBeatInterval() {
        return heartBeatInterval;
    }

    public boolean isSendheartBeat() {
        return isSendheartBeat;
    }

    public boolean getConnectStatus() {
        return isConnect;
    }

    public boolean isConnecting() {
        return isConnecting;
    }

    public void setConnectStatus(boolean status) {
        this.isConnect = status;
    }

    public void setClientListener(NettyClientListener listener) {
        this.nettyClientListener = listener;
    }

    public static class Builder {
        private int MAX_CONNECT_TIMES = Integer.MAX_VALUE;//最大重连次数
        private long reconnectIntervalTime = 5000;//重连间隔
        private String host;//服务器地址
        private int tcp_port;//服务器端口
        private int mIndex;//客户端标识，(因为可能存在多个连接)
        private boolean isSendheartBeat;//是否发送心跳
        private long heartBeatInterval = 5;//心跳时间间隔
        private Object heartBeatData;//心跳数据，可以是String类型，也可以是byte[].
        private String packetSeparator;
        private int maxPacketLong = 1024;

        public Builder() {
            this.maxPacketLong = 1024;
        }

        public Builder setPacketSeparator(String packetSeparator) {
            this.packetSeparator = packetSeparator;
            return this;
        }

        public Builder setMaxPacketLong(int maxPacketLong) {
            this.maxPacketLong = maxPacketLong;
            return this;
        }

        public Builder setMaxReconnectTimes(int reConnectTimes) {
            this.MAX_CONNECT_TIMES = reConnectTimes;
            return this;
        }

        public Builder setReconnectIntervalTime(long reconnectIntervalTime) {
            this.reconnectIntervalTime = reconnectIntervalTime;
            return this;
        }

        public Builder setHost(String host) {
            this.host = host;
            return this;
        }

        public Builder setTcpPort(int tcp_port) {
            this.tcp_port = tcp_port;
            return this;
        }

        public Builder setIndex(int mIndex) {
            this.mIndex = mIndex;
            return this;
        }

        public Builder setHeartBeatInterval(long intervalTime) {
            this.heartBeatInterval = intervalTime;
            return this;
        }

        public Builder setSendheartBeat(boolean isSendheartBeat) {
            this.isSendheartBeat = isSendheartBeat;
            return this;
        }

        public Builder setHeartBeatData(Object heartBeatData) {
            this.heartBeatData = heartBeatData;
            return this;
        }

        public TcpClient build() {
            TcpClient nettyTcpClient = new TcpClient(host, tcp_port, mIndex);
            nettyTcpClient.MAX_CONNECT_TIMES = this.MAX_CONNECT_TIMES;
            nettyTcpClient.reconnectIntervalTime = this.reconnectIntervalTime;
            nettyTcpClient.heartBeatInterval = this.heartBeatInterval;
            nettyTcpClient.isSendheartBeat = this.isSendheartBeat;
            nettyTcpClient.heartBeatData = this.heartBeatData;
            nettyTcpClient.packetSeparator = this.packetSeparator;
            nettyTcpClient.maxPacketLong = this.maxPacketLong;
            return nettyTcpClient;
        }
    }
}
