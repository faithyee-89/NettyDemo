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

import com.littlegreens.netty.client.TcpManager;
import com.littlegreens.netty.client.listener.MessageStateListener;
import com.littlegreens.netty.client.listener.NettyClientListener;
import com.littlegreens.netty.client.status.ConnectState;

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
    private Button mBtDisconnect;
    private EditText mEtSend;
    private RecyclerView mRvSendList;
    private RecyclerView mRvReceList;

    private LogAdapter mSendLogAdapter = new LogAdapter();
    private LogAdapter mReceLogAdapter = new LogAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViews();
        initView();
    }

    private void initView() {
        LinearLayoutManager manager1 = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mRvSendList.setLayoutManager(manager1);
        mRvSendList.setAdapter(mSendLogAdapter);

        LinearLayoutManager manager2 = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mRvReceList.setLayoutManager(manager2);
        mRvReceList.setAdapter(mReceLogAdapter);
        TcpManager.getInstance().getTcpClient().setClientListener(this);
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
        mBtDisconnect = findViewById(R.id.bt_disconnect);

        mBtConnect.setOnClickListener(this);
        mBtSendBtn.setOnClickListener(this);
        mBtClearLog.setOnClickListener(this);
        mBtOpenShutter.setOnClickListener(this);
        mBtCloseShutter.setOnClickListener(this);
        mBtLightState.setOnClickListener(this);
        mBtVolumeState.setOnClickListener(this);
        mBtDisconnect.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.connect:
                break;

            case R.id.send_btn:
                if (!TcpManager.getInstance().getTcpClient().getConnectStatus()) {
                    Toast.makeText(getApplicationContext(), "未连接,请先连接", Toast.LENGTH_SHORT).show();
                } else {
                    final String msg = mEtSend.getText().toString();
                    if (TextUtils.isEmpty(msg.trim())) {
                        return;
                    }
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
                TcpManager.getInstance().sendMessage(Const.HOST, Const.TCP_PORT, Const.openShutterCmdBytes, new MessageStateListener() {
                    @Override
                    public void isSendSuccss(boolean isSuccess) {
                        if (isSuccess) {
                            logSend("发送成功");
                        }
                    }
                });
                break;
            case R.id.bt_close_shutter:
//                String closeShutterCmd = "AT+LightSource=Off<CR>";
//                byte[] closeShutterCmdBytes = closeShutterCmd.getBytes(StandardCharsets.UTF_8);
                TcpManager.getInstance().sendMessage(Const.HOST, Const.TCP_PORT, Const.closeShutterCmdBytes, new MessageStateListener() {
                    @Override
                    public void isSendSuccss(boolean isSuccess) {
                        if (isSuccess) {
                            logSend("发送成功");
                        }
                    }
                });
                break;
            case R.id.bt_get_light:
                break;
            case R.id.bt_get_volume:
                break;

            case R.id.bt_disconnect:
                TcpManager.getInstance().disConnect();
                break;
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
}
