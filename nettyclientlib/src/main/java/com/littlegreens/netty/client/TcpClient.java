package com.littlegreens.netty.client;

import android.text.TextUtils;
import android.util.Log;

import com.littlegreens.netty.client.handler.NettyClientHandler;
import com.littlegreens.netty.client.listener.MessageStateListener;
import com.littlegreens.netty.client.listener.NettyClientListener;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
    private boolean isConnect = false;
    private int MAX_CONNECT_TIMES = Integer.MAX_VALUE; //最大重连次数
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
    private ConcurrentHashMap<String, ChannelWrapper> channelTables;//负责维护设备地址和对应的通道
    private final Lock lockChannelTables = new ReentrantLock();
    private static final long LOCK_TIMEOUT_MILLIS = 3000;//锁超时时间

    private TcpClient(String ipAddr, int port, int index) {
        this.ipAddr = ipAddr;
        this.port = port;
        this.mIndex = index;
        initConfig();
        initClient();
    }

    private void initConfig() {
        channelTables = new ConcurrentHashMap<String, ChannelWrapper>();
        eventLoopGroupWorker = new NioEventLoopGroup();
    }

    private void initClient() {
        mBootstrap
                .group(eventLoopGroupWorker)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)//屏蔽Nagle算法试图
                .option(ChannelOption.SO_KEEPALIVE, false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
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

    public void disconnect() {
        Log.e(TAG, "disconnect");
        try {
            for (ChannelWrapper cw : this.channelTables.values()) {
                closeChannel(null, cw.getChannel());
            }
            this.channelTables.clear();
            this.eventLoopGroupWorker.shutdownGracefully();

        } catch (Exception e) {
            Log.e(TAG, "NettyRemotingClient shutdown exception, ", e);
        }
    }

    public void closeChannel(String addr, Channel channel) {
        if (null == channel) {
            return;
        }
        String addrRemote = null == addr ? RemotingUtil.parseChannelRemoteAddr(channel) : addr;
        try {
            if (this.lockChannelTables.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MICROSECONDS)) {
                try {
                    boolean removeItemFromTable = true;
                    ChannelWrapper prevCW = channelTables.get(addrRemote);
                    Log.i(TAG, "closeChannel: begin close the channel[" + addrRemote + "] Found: " + (prevCW != null));

                    if (prevCW == null) {
                        Log.i(TAG, "the channel " + addrRemote + " has been removed");
                        removeItemFromTable = false;
                    } else if (prevCW.getChannel() != channel) {
                        Log.i(TAG, "the channel[" + addrRemote + "] has been closed before and has been created again");
                        removeItemFromTable = false;
                    }
                    if (removeItemFromTable) {
                        channelTables.remove(addrRemote);
                        Log.i(TAG, "the channel[" + addrRemote + "] was removed from channel table");
                    }
                    RemotingUtil.closeChannel(channel);
                } catch (Exception e) {
                    Log.e(TAG, "closeChannel: close the channel exception", e);
                } finally {
                    this.lockChannelTables.unlock();
                }
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "closeChannel exception", e);
        }
    }

    /**
     * 异步发送
     *
     * @param msg      发送int数据
     * @param listener 发送状态回调
     * @return 是否已发送
     */
    public boolean sendMsgToServer(String ipAddr, int port, int[] msg, final MessageStateListener listener) throws InterruptedException {
        Channel andCreateChannel = getAndCreateChannel(ipAddr, port);
        boolean flag = andCreateChannel != null;
        if (flag) {
            ByteBuf sendData = ByteBufAllocator.DEFAULT.buffer();
            for (int i = 0; i < msg.length; i++) {
                sendData.writeByte(msg[i]);
            }

            andCreateChannel.writeAndFlush(
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

    public boolean sendMsgToServer(String ipAddr, int port, String data) throws InterruptedException {
        Channel channel = getAndCreateChannel(ipAddr, port);
        boolean flag = channel != null;
        if (flag) {
            String separator = TextUtils.isEmpty(packetSeparator) ? System.getProperty("line.separator") : packetSeparator;
            ChannelFuture channelFuture = channel.writeAndFlush(data + separator).awaitUninterruptibly();
            return channelFuture.isSuccess();
        }
        return false;
    }

    private Channel getAndCreateChannel(String ipAddr, int port) throws InterruptedException {
        ChannelWrapper cw = channelTables.get(ipAddr);
        if (cw != null && cw.isAlive()) {
            return cw.getChannel();
        }
        return createChannel(ipAddr, port);
    }

    private Channel createChannel(String ipAddr, int port) throws InterruptedException {
        ChannelWrapper channelWrapper = this.channelTables.get(ipAddr);
        if (channelWrapper != null && channelWrapper.isAlive()) {
            return channelWrapper.getChannel();
        }

        if (this.lockChannelTables.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MICROSECONDS)) {
            try {
                boolean createNewConnection;
                channelWrapper = this.channelTables.get(ipAddr);
                if (channelWrapper != null) {
                    if (channelWrapper.isAlive()) {
                        return channelWrapper.getChannel();
                    } else if (!channelWrapper.getChannelFuture().isDone()) {
                        createNewConnection = false;
                    } else {
                        createNewConnection = true;
                    }
                } else {
                    createNewConnection = true;
                }

                if (createNewConnection) {
                    ChannelFuture channelFuture = mBootstrap.connect(ipAddr, port);
                    channelWrapper = new ChannelWrapper(channelFuture);
                    this.channelTables.put(ipAddr, channelWrapper);
                }
            } catch (Exception e) {
                Log.e(TAG, "create channel exception" + e);
            } finally {
                this.lockChannelTables.unlock();
            }
        } else {
            Log.w(TAG, "try to lock channel table timeout, " + LOCK_TIMEOUT_MILLIS + " ms");
        }

        if (channelWrapper != null) {
            ChannelFuture channelFuture = channelWrapper.getChannelFuture();
            if (channelFuture.awaitUninterruptibly(CONNECT_TIMEOUT_MILLIS)) {
                if (channelWrapper.isAlive()) {
                    Log.i(TAG, "createChannel: connect remote host[" + ipAddr + "] success, " + channelFuture.toString());
                    return channelWrapper.getChannel();
                } else {
                    Log.w(TAG, "createChannel: connect remote host[" + ipAddr + "] failed, " + channelFuture.toString(), channelFuture.cause());
                }
            } else {
                Log.w(TAG, "connect remote host " + ipAddr + ", timeout " + CONNECT_TIMEOUT_MILLIS + " ms");
            }
        }
        return null;
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

    /**
     * 通道装饰类
     */
    static class ChannelWrapper {
        private final ChannelFuture channelFuture;

        public ChannelWrapper(ChannelFuture channelFuture) {
            this.channelFuture = channelFuture;
        }

        public boolean isAlive() {
            return this.channelFuture.channel() != null && this.channelFuture.channel().isActive();
        }

        public boolean isWritable() {
            return this.channelFuture.channel().isWritable();
        }

        private Channel getChannel() {
            return this.channelFuture.channel();
        }

        public ChannelFuture getChannelFuture() {
            return channelFuture;
        }
    }
}
