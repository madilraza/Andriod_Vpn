package com.example.vpn_sample_2;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MyVpnService extends VpnService {

    private static final String TAG = "MyVpnService";
    private ParcelFileDescriptor vpnInterface;
    private List<String> imageExtensions;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Handler handler = new Handler(Looper.getMainLooper());
    private OkHttpClient client = new OkHttpClient();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getExtras() != null) {
            imageExtensions = intent.getStringArrayListExtra("imageExtensions");

            Thread vpnThread = new Thread(this::runVpnConnection);
            vpnThread.start();
        }
        return START_STICKY;
    }

    private void runVpnConnection() {
        try {
            if (establishVpnConnection()) {
                Log.i(TAG, "VPN interface established successfully.");
                isRunning.set(true);
                interceptPackets();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during VPN connection: " + e.getMessage(), e);
        } finally {
            stopVpnConnection();
        }
    }

    private boolean establishVpnConnection() {
        if (vpnInterface == null) {
            Builder builder = new Builder();
            try {
                builder.addAddress("10.0.2.15", 24)
                        .addRoute("0.0.0.0", 0)
                        .addDnsServer("8.8.8.8")
                        .addDnsServer("8.8.4.4");

                PendingIntent configureIntent = PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE);

                vpnInterface = builder.setSession("MyVPN")
                        .setConfigureIntent(configureIntent)
                        .establish();

                return vpnInterface != null;

            } catch (Exception e) {
                Log.e(TAG, "Failed to establish VPN interface: " + e.getMessage(), e);
            }
        } else {
            handler.post(() -> Toast.makeText(MyVpnService.this, "VPN connection already established", Toast.LENGTH_SHORT).show());
        }
        return false;
    }

    private void stopVpnConnection() {
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
                vpnInterface = null;
                isRunning.set(false);
                Log.i(TAG, "VPN interface closed.");
            } catch (IOException e) {
                Log.e(TAG, "Failed to close VPN interface: " + e.getMessage(), e);
            }
        }
    }

    private void interceptPackets() {
        if (vpnInterface == null) {
            Log.e(TAG, "VPN interface is null. Cannot intercept packets.");
            return;
        }

        try (FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor())) {

            ByteBuffer packet = ByteBuffer.allocate(32767);
            while (isRunning.get()) {
                int length = in.read(packet.array());
                if (length > 0) {
                    packet.limit(length);

                    Log.i(TAG, "Packet intercepted: " + new String(packet.array(), 0, packet.limit()));

                    if (isHttpPacket(packet)) {
                        String httpPayload = extractHttpPayload(packet);
                        if (httpPayload != null && isImageRequest(httpPayload, imageExtensions)) {
                            String imageUrl = extractImageUrl(httpPayload);

                            Log.i(TAG, "Filtered Image Request: " + imageUrl);

                            new Thread(() -> {
                                try {
                                    String response = validateImageUrl(imageUrl);
                                    Log.i(TAG, "Server Response: " + response);
                                    handleServerResponse(response, packet, out, imageUrl);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error validating image URL: " + e.getMessage(), e);
                                }
                            }).start();
                        } else {
                            out.write(packet.array(), 0, length);
                        }
                    } else {
                        out.write(packet.array(), 0, length);
                    }
                    packet.clear();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error intercepting packets: " + e.getMessage(), e);
        }
    }

    private boolean isHttpPacket(ByteBuffer packet) {
        int position = packet.position();
        byte[] array = packet.array();
        return array.length > 4 &&
                ((array[position] == 'G' && array[position + 1] == 'E' && array[position + 2] == 'T') ||
                        (array[position] == 'P' && array[position + 1] == 'O' && array[position + 2] == 'S' && array[position + 3] == 'T'));
    }

    private String extractHttpPayload(ByteBuffer packet) {
        int position = packet.position();
        return new String(packet.array(), position, packet.limit() - position);
    }

    private boolean isImageRequest(String httpPayload, List<String> imageExtensions) {
        return imageExtensions.stream().anyMatch(httpPayload::contains);
    }

    private String extractImageUrl(String httpPayload) {
        int start = httpPayload.indexOf("GET ") + 4;
        int end = httpPayload.indexOf(" ", start);
        if (start > 0 && end > 0) {
            return httpPayload.substring(start, end);
        }
        return null;
    }

    private String validateImageUrl(String imageUrl) throws IOException {
        Request request = new Request.Builder()
                .url("https://kp.aysko.net/info")
                .post(RequestBody.create(imageUrl, MediaType.parse("text/plain")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body() != null ? response.body().string() : null;
        }
    }

    private void handleServerResponse(String response, ByteBuffer packet, FileOutputStream out, String imageUrl) {
        if (response != null && response.contains("\"allowed\":true")) {
            try {
                out.write(packet.array(), 0, packet.limit());
            } catch (IOException e) {
                Log.e(TAG, "Error forwarding packet: " + e.getMessage(), e);
            }
        } else {
            Log.i(TAG, "Image blocked by server: " + imageUrl);
        }
    }

    @Override
    public void onDestroy() {
        stopVpnConnection();
        super.onDestroy();
    }
}
