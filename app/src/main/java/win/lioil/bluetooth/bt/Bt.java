package win.lioil.bluetooth.bt;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Environment;
import android.text.TextUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.UUID;

import win.lioil.bluetooth.MainAPP;
import win.lioil.bluetooth.Util;

/**
 * 客户端和服务端公共部分，主要用于管理socket
 */
public class Bt {
    static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    static final String SPP_TAG = "SPP_TAG";
    private static final String FILE_TAG = "::FILE_TAG::"; //文件标志
    private static final String FILE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/bt/";

    BluetoothSocket mSocket;
    private DataOutputStream mOut;
    private Listener mListener;
    private boolean isRead;
    private boolean isSending;

    Bt(Listener listener) {
        mListener = listener;
    }

    /**
     * 循环接收数据(若没有数据，则阻塞等待)
     */
    void loopRead() {
        try {
            if (!mSocket.isConnected())
                mSocket.connect();
            notifyUI(Listener.CONNECTED, mSocket.getRemoteDevice());
            mOut = new DataOutputStream(mSocket.getOutputStream());
            DataInputStream in = new DataInputStream(mSocket.getInputStream());
            isRead = true;
            while (isRead) { //死循环读取
                String msg = in.readUTF();
                if (!FILE_TAG.equals(msg)) {// 接收短消息
                    notifyUI(Listener.MSG, "接收短消息：" + msg);
                } else {// 接收文件
                    Util.mkdirs(FILE_PATH);
                    String fileName = in.readUTF(); //文件名
                    long fileLen = in.readLong(); //文件长度
                    notifyUI(Listener.MSG, "正在接收文件(" + fileName + ")...");
                    // 读取文件内容
                    long len = 0;
                    int r;
                    byte[] b = new byte[4 * 1024];
                    FileOutputStream out = new FileOutputStream(FILE_PATH + fileName);
                    while ((r = in.read(b)) != -1) {
                        out.write(b, 0, r);
                        len += r;
                        if (len >= fileLen)
                            break;
                    }
                    notifyUI(Listener.MSG, "文件接收完成(存放在:" + FILE_PATH + ")");
                }
            }
        } catch (Throwable e) {
            close();
        }
    }

    /**
     * 发送短消息
     */
    public void sendMsg(String msg) {
        if (isSending || TextUtils.isEmpty(msg))
            return;
        isSending = true;
        try {
            mOut.writeUTF(msg);
        } catch (Throwable e) {
            close();
        }
        notifyUI(Listener.MSG, "发送短消息：" + msg);
        isSending = false;
    }

    /**
     * 发送文件
     */
    public void sendFile(final String filePath) {
        if (isSending || TextUtils.isEmpty(filePath))
            return;
        isSending = true;
        Util.EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    notifyUI(Listener.MSG, "正在发送文件(" + filePath + ")...");
                    FileInputStream in = new FileInputStream(filePath);
                    File file = new File(filePath);
                    mOut.writeUTF(FILE_TAG); //文件标志(自定义)
                    mOut.writeUTF(file.getName()); //文件名
                    mOut.writeLong(file.length()); //文件长度
                    int r;
                    byte[] b = new byte[4 * 1024];
                    while ((r = in.read(b)) != -1) {
                        mOut.write(b, 0, r);
                    }
                    notifyUI(Listener.MSG, "文件发送完成.");
                } catch (Throwable e) {
                    close();
                }
                isSending = false;
            }
        });
    }

    public void close() {
        try {
            isRead = false;
            mSocket.close();
            notifyUI(Listener.DISCONNECTED, null);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public boolean isConnected(BluetoothDevice dev) {
        boolean connected = (mSocket != null && mSocket.isConnected());
        if (dev == null)
            return connected;
        return connected && mSocket.getRemoteDevice().equals(dev);
    }

    // ============================================通知UI===========================================================
    private void notifyUI(final int state, final Object obj) {
        MainAPP.runUi(new Runnable() {
            @Override
            public void run() {
                try {
                    mListener.socketNotify(state, obj);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public interface Listener {
        int DISCONNECTED = 0;
        int CONNECTED = 1;
        int MSG = 2;

        void socketNotify(int state, Object obj);
    }
}
