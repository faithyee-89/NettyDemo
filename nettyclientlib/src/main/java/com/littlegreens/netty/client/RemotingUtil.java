package com.littlegreens.netty.client;

import android.util.Log;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

/**
 * 工具类
 *
 * @author hardy liu
 * @version 1.0
 * @date 2022/10/22 15:01
 */
public class RemotingUtil {
    private static final String TAG = "RemotingUtil";

    public static String parseTemplateDefaultVal(String template) {
        String reg = "defaultvalue:\"(.*?)\"";
        Pattern pattern = Pattern.compile(reg);
        Matcher matcher = pattern.matcher(template);
        while (matcher.find()) {
            String findStr = matcher.group(1);
            return findStr;
        }
        return null;
    }

    public static String parseTemplateType(String template) {
        String patternStr = "#\\{\\{appotype:\"(.*?)\"";
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(template);
        //默认为string
        String findStr = "string";
        while (matcher.find()) {
            findStr = matcher.group(1);
            break;

        }

        return findStr;
    }

    public static String parseTemplateCommand(String template, String requestTypePattern) {
        Pattern pattern = Pattern.compile(requestTypePattern);
        Matcher matcher = pattern.matcher(template);
        while (matcher.find()) {
            String findStr = matcher.group(1);
            return findStr;
        }
        return null;
    }

    public static String exceptionSimpleDesc(final Throwable e) {
        StringBuffer sb = new StringBuffer();
        if (e != null) {
            sb.append(e);

            StackTraceElement[] stackTrace = e.getStackTrace();
            if (stackTrace != null && stackTrace.length > 0) {
                StackTraceElement element = stackTrace[0];
                sb.append(", ");
                sb.append(element.toString());
            }
        }

        return sb.toString();
    }

    /**
     * 关闭通道
     *
     * @param channel
     */
    public static void closeChannel(Channel channel) {
        final String addrRemote = parseChannelRemoteAddr(channel);
        channel.close().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                Log.i(TAG, "closeChannel: close the connection to remote address[" + addrRemote + "] result: " + future.isSuccess());
            }
        });
    }


    /**
     * 将字符串形式地址192.168.1.110:5000转为SocketAddress
     *
     * @param addr
     * @return
     */
    public static SocketAddress string2SocketAddress(final String addr) {
        int split = addr.lastIndexOf(":");
        String host = addr.substring(0, split);
        String port = addr.substring(split + 1);
        InetSocketAddress isa = new InetSocketAddress(host, Integer.parseInt(port));
        return isa;
    }

    /**
     * 从通道解析出地址信息
     *
     * @param channel
     * @return
     */
    public static String parseChannelRemoteAddr(final Channel channel) {
        if (null == channel) {
            return "";
        }
        SocketAddress remote = channel.remoteAddress();
        final String addr = remote != null ? remote.toString() : "";
        if (addr.length() > 0) {
            int index = addr.lastIndexOf("/");
            if (index >= 0) {
                return addr.substring(index + 1);
            }
            return addr;
        }
        return "";
    }

    /**
     * 将SocketAddress转换为字符串形式
     *
     * @param socketAddress
     * @return
     */
    public static String parseSocketAddressAddr(SocketAddress socketAddress) {
        if (socketAddress != null) {
            final String addr = socketAddress.toString();

            if (addr.length() > 0) {
                return addr.substring(1);
            }
        }
        return "";
    }

}
