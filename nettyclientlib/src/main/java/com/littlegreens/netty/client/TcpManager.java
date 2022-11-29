package com.littlegreens.netty.client;

import com.littlegreens.netty.client.listener.MessageStateListener;

/**
 * @desc: 设备通讯入口类
 * @author: yewei
 * @data: 2022/11/10 10:12
 */
public class TcpManager {
    private static volatile TcpManager instance;
    private TcpClient tcpClient;

    public static TcpManager getInstance() {
        if (instance == null) {
            synchronized (TcpManager.class) {
                if (instance == null) {
                    instance = new TcpManager();
                }
            }
        }
        return instance;
    }

    private TcpManager() {
        init();
    }

    private void init() {
        initNettyClient();
    }

    public TcpClient getTcpClient() {
        return tcpClient;
    }

    private void initNettyClient() {
        tcpClient = new TcpClient.Builder()
                .setMaxReconnectTimes(1)    //设置最大重连次数
                .setReconnectIntervalTime(5)    //设置重连间隔时间。单位：秒
                .setSendheartBeat(true) //设置是否发送心跳
                .setHeartBeatInterval(5)    //设置心跳间隔时间。单位：秒
                .setHeartBeatData("I'm is HeartBeatData") //设置心跳数据，可以是String类型，也可以是byte[]，以后设置的为准
                .setIndex(0)    //设置客户端标识.(因为可能存在多个tcp连接)
//                .setPacketSeparator("#")//用特殊字符，作为分隔符，解决粘包问题，默认是用换行符作为分隔符
//                .setMaxPacketLong(1024)//设置一次发送数据的最大长度，默认是1024
                .build();
    }

    public void sendMessage(String ipAddr, int port, int[] msg, final MessageStateListener listener) {
        try {
            tcpClient.sendMsgToServer(ipAddr, port, msg, listener);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void disConnect() {
        tcpClient.disconnect();
    }
}
