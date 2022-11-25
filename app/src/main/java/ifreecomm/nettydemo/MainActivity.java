package ifreecomm.nettydemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.littlegreens.netty.client.listener.MessageStateListener;
import com.littlegreens.netty.client.listener.NettyClientListener;
import com.littlegreens.netty.client.NettyTcpClient;
import com.littlegreens.netty.client.status.ConnectState;

import java.nio.charset.StandardCharsets;

import ifreecomm.nettydemo.adapter.LogAdapter;
import ifreecomm.nettydemo.bean.LogBean;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, NettyClientListener<String> {

    private static final String TAG = "MainActivity";
    private Button mBtClearLog;
    private Button mBtSendBtn;
    private Button mBtConnect;
    private Button mBtOpenShutter;
    private Button mBtCloseShutter;
    private Button mBtLightState;
    private Button mBtVolumeState;
    private EditText mEtSend;
    private RecyclerView mRvSendList;
    private RecyclerView mRvReceList;

    private LogAdapter mSendLogAdapter = new LogAdapter();
    private LogAdapter mReceLogAdapter = new LogAdapter();
    private NettyTcpClient mNettyTcpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViews();
        initView();

        mNettyTcpClient = new NettyTcpClient.Builder()
                .setHost(Const.HOST)    //设置服务端地址
                .setTcpPort(Const.TCP_PORT) //设置服务端端口号
                .setMaxReconnectTimes(1)    //设置最大重连次数
                .setReconnectIntervalTime(5)    //设置重连间隔时间。单位：秒
                .setSendheartBeat(true) //设置是否发送心跳
                .setHeartBeatInterval(5)    //设置心跳间隔时间。单位：秒
                .setHeartBeatData("I'm is HeartBeatData") //设置心跳数据，可以是String类型，也可以是byte[]，以后设置的为准
                .setIndex(0)    //设置客户端标识.(因为可能存在多个tcp连接)
//                .setPacketSeparator("#")//用特殊字符，作为分隔符，解决粘包问题，默认是用换行符作为分隔符
//                .setMaxPacketLong(1024)//设置一次发送数据的最大长度，默认是1024
                .build();

        mNettyTcpClient.setListener(MainActivity.this); //设置TCP监听
    }

    private void initView() {
        LinearLayoutManager manager1 = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mRvSendList.setLayoutManager(manager1);
        mRvSendList.setAdapter(mSendLogAdapter);

        LinearLayoutManager manager2 = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mRvReceList.setLayoutManager(manager2);
        mRvReceList.setAdapter(mReceLogAdapter);

    }

    private void findViews() {
        mRvSendList = findViewById(R.id.send_list);
        mRvReceList = findViewById(R.id.rece_list);
        mEtSend = findViewById(R.id.send_et);
        mBtConnect = findViewById(R.id.connect);
        mBtSendBtn = findViewById(R.id.send_btn);
        mBtClearLog = findViewById(R.id.clear_log);

        mBtOpenShutter = findViewById(R.id.bt_open_shutter);
        mBtCloseShutter = findViewById(R.id.bt_close_shutter);
        mBtLightState = findViewById(R.id.bt_get_light);
        mBtVolumeState = findViewById(R.id.bt_get_volume);

        mBtConnect.setOnClickListener(this);
        mBtSendBtn.setOnClickListener(this);
        mBtClearLog.setOnClickListener(this);
        mBtOpenShutter.setOnClickListener(this);
        mBtCloseShutter.setOnClickListener(this);
        mBtLightState.setOnClickListener(this);
        mBtVolumeState.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.connect:
                connect();
                break;

            case R.id.send_btn:
                if (!mNettyTcpClient.getConnectStatus()) {
                    Toast.makeText(getApplicationContext(), "未连接,请先连接", Toast.LENGTH_SHORT).show();
                } else {
                    final String msg = mEtSend.getText().toString();
                    if (TextUtils.isEmpty(msg.trim())) {
                        return;
                    }
//                    Byte[] commandArrays = msg.getBytes(StandardCharsets.UTF_8);


                    mNettyTcpClient.sendMsgToServer(msg, new MessageStateListener() {
                        @Override
                        public void isSendSuccss(boolean isSuccess) {
                            if (isSuccess) {
                                Log.d(TAG, "Write auth successful");
                                logSend(msg);
                            } else {
                                Log.d(TAG, "Write auth error");
                            }
                        }
                    });
                    mEtSend.setText("");
                }

                break;

            case R.id.clear_log:
                mReceLogAdapter.getDataList().clear();
                mSendLogAdapter.getDataList().clear();
                mReceLogAdapter.notifyDataSetChanged();
                mSendLogAdapter.notifyDataSetChanged();
                break;

            case R.id.bt_open_shutter:
//                String openShutterCmd = "AT+LightSource=On<CR>";
//                byte[] openShutterCmdBytes = openShutterCmd.getBytes(StandardCharsets.UTF_8);
                byte[] openShutterCmdBytes = {0x41, 0x54, 0x2B, 0x4C, 0x69, 0x67, 0x68, 0x74, 0x53, 0x6F, 0x75, 0x72, 0x63, 0x65, 0x3D, 0x4F, 0x6E, 0x0D};

                mNettyTcpClient.sendMsgToServer(openShutterCmdBytes, new MessageStateListener() {
                    @Override
                    public void isSendSuccss(boolean isSuccess) {
                        if (isSuccess) {
                            logSend("发送成功");
                            return;
                        }
                        logSend("发送失败");
                    }
                });
                break;
            case R.id.bt_close_shutter:
//                String closeShutterCmd = "AT+LightSource=Off<CR>";
//                byte[] closeShutterCmdBytes = closeShutterCmd.getBytes(StandardCharsets.UTF_8);
                byte[] closeShutterCmdBytes = {0x41, 0x54, 0x2B, 0x4C, 0x69, 0x67, 0x68, 0x74, 0x53, 0x6F, 0x75, 0x72, 0x63, 0x65, 0x3D, 0x4F, 0x66, 0x66,0x0D};

                mNettyTcpClient.sendMsgToServer(closeShutterCmdBytes, new MessageStateListener() {
                    @Override
                    public void isSendSuccss(boolean isSuccess) {
                        if (isSuccess) {
                            logSend("发送成功");
                            return;
                        }
                        logSend("发送失败");
                    }
                });
                break;
            case R.id.bt_get_light:
                break;
            case R.id.bt_get_volume:
                break;
        }
    }

    private void connect() {
        Log.d(TAG, "connect");
        if (!mNettyTcpClient.getConnectStatus()) {
            mNettyTcpClient.connect();//连接服务器
        } else {
            mNettyTcpClient.disconnect();
        }
    }

    @Override
    public void onMessageResponseClient(String msg, int index) {
        Log.e(TAG, "onMessageResponse:" + msg);
        logRece(index + ":" + msg);
    }

    @Override
    public void onClientStatusConnectChanged(final int statusCode, final int index) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (statusCode == ConnectState.STATUS_CONNECT_SUCCESS) {
                    Log.e(TAG, "STATUS_CONNECT_SUCCESS:");
                    mBtConnect.setText("DisConnect:" + index);
                } else {
                    Log.e(TAG, "onServiceStatusConnectChanged:" + statusCode);
                    mBtConnect.setText("Connect:" + index);
                }
            }
        });

    }

    private void logSend(String log) {
        LogBean logBean = new LogBean(System.currentTimeMillis(), log);
        mSendLogAdapter.getDataList().add(0, logBean);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSendLogAdapter.notifyDataSetChanged();
            }
        });

    }

    private void logRece(String log) {
        LogBean logBean = new LogBean(System.currentTimeMillis(), log);
        mReceLogAdapter.getDataList().add(0, logBean);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mReceLogAdapter.notifyDataSetChanged();
            }
        });

    }

    /**
     * 方法三：
     * byte[] to hex string
     *
     * @param bytes
     * @return
     */
    public static String bytesToHexFun3(byte[] bytes, int length) {
        StringBuilder buf = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {// 使用String的format方法进行转换
            buf.append(String.format("%02x", new Integer(bytes[i] & 0xFF)));
        }
        return buf.toString();
    }

    public void disconnect(View view) {
        mNettyTcpClient.disconnect();
    }
}
