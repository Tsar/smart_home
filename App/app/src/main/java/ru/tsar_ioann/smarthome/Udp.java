package ru.tsar_ioann.smarthome;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class Udp {
    private static final String LOG_TAG = "Udp";
    private static final int RECEIVE_BUFFER_SIZE = 256;

    public interface Listener {
        boolean finish();
        void onReceive(String message, String senderIp, int senderPort);
        void onError(IOException exception);
        void onFatalError(SocketException exception);
    }

    public static void asyncListen(int port, Listener listener) {
        new Thread() {
            @Override
            public void run() {
                DatagramSocket socket;
                try {
                    socket = new DatagramSocket(port);
                    socket.setSoTimeout(5000);  // timeout is needed to exit from cycle below
                } catch (SocketException e) {
                    listener.onFatalError(e);
                    return;
                }
                byte[] buffer = new byte[RECEIVE_BUFFER_SIZE];
                while (!listener.finish()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(packet);
                        listener.onReceive(
                                new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8),
                                packet.getAddress().toString(),
                                packet.getPort()
                        );
                    } catch (SocketTimeoutException e) {
                        // skip timeout
                    } catch (IOException exception) {
                        listener.onError(exception);
                    }
                }
                socket.close();
            }
        }.start();
    }

    public static void asyncMulticastNoThrow(String ip, int port, String message) {
        new Thread() {
            @Override
            public void run() {
                try {
                    multicast(ip, port, message);
                } catch (IOException e) {
                    Log.d(LOG_TAG, "Multicast failed: " + e.getMessage());
                }
            }
        }.start();
    }

    public static void multicast(String ip, int port, String message) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        byte[] buffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(ip), port);
        socket.send(packet);
        socket.close();
        Log.d(LOG_TAG, "Multicast sent");
    }
}
