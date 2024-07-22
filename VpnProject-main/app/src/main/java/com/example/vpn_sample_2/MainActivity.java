package com.example.vpn_sample_2;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private static final int VPN_REQUEST_CODE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnStartVpn = findViewById(R.id.btn_start_vpn);

        btnStartVpn.setOnClickListener(v -> startVpn());
    }

    private void startVpn() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE);
        } else {
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            Intent vpnIntent = new Intent(this, MyVpnService.class);
            vpnIntent.putStringArrayListExtra("imageExtensions", new ArrayList<>(Arrays.asList(".jpg", ".jpeg", ".png")));
            startService(vpnIntent);
            Toast.makeText(this, "VPN started", Toast.LENGTH_SHORT).show();
        }
    }
}
