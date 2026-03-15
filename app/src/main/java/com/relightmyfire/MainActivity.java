package com.relightmyfire;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final String MODEL_FORD     = "KFFOWI";
    private static final String MODEL_AUSTIN   = "KFAUWI";
    private static final String MODEL_LINEAGE  = "Fire";

    private TextView    tvDeviceInfo;
    private TextView    tvOutput;
    private TextView    tvPrompt;
    private ScrollView  scrollOutput;
    private Button      btnExploit;
    private EditText    etInput;
    private Button      btnSend;
    private LinearLayout terminalBox;
    private ImageView    ivFooter;

    private String    detectedGen = "austin";
    private Process   currentProcess;
    private OutputStream processStdin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvDeviceInfo = (TextView)      findViewById(R.id.tvDeviceInfo);
        tvOutput     = (TextView)      findViewById(R.id.tvOutput);
        tvPrompt     = (TextView)      findViewById(R.id.tvPrompt);
        scrollOutput = (ScrollView)    findViewById(R.id.scrollOutput);
        btnExploit   = (Button)        findViewById(R.id.btnExploit);
        etInput      = (EditText)      findViewById(R.id.etInput);
        btnSend      = (Button)        findViewById(R.id.btnSend);
        terminalBox  = (LinearLayout)  findViewById(R.id.terminalBox);
        ivFooter     = (ImageView)     findViewById(R.id.ivFooter);

        detectedGen = detectVariant();

        tvDeviceInfo.setText(
            "Model: " + Build.MODEL + "  SDK: " + Build.VERSION.SDK_INT
            + "  Arch: " + System.getProperty("os.arch")
            + "\nUsing profile: " + detectedGen
        );

        tvOutput.setTypeface(Typeface.MONOSPACE);
        tvPrompt.setTypeface(Typeface.MONOSPACE);
        etInput.setTypeface(Typeface.MONOSPACE);

        // Tap anywhere on the terminal box → focus input
        terminalBox.setOnClickListener(v -> etInput.requestFocus());
        scrollOutput.setOnClickListener(v -> etInput.requestFocus());
        tvOutput.setOnClickListener(v -> etInput.requestFocus());

        // Size footer watermark to 1/3 of terminal box width after layout
        terminalBox.post(() -> {
            int w = terminalBox.getWidth() * 2 / 5;
            android.view.ViewGroup.LayoutParams lp = ivFooter.getLayoutParams();
            lp.width = w;
            ivFooter.setLayoutParams(lp);
        });

        btnExploit.setOnClickListener(v -> runExploit());
        btnSend.setOnClickListener(v -> sendInput());
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                sendInput();
                return true;
            }
            return false;
        });
    }

    /**
     * Detects device variant using system properties for reliable
     * differentiation between stock Fire OS and LineageOS builds.
     *
     * Key props used:
     *   ro.product.name  → "lineage_austin" / "lineage_ford" on LineageOS
     *   ro.build.tags    → "test-keys" on LineageOS, "release-keys" on stock
     *   ro.build.display.id → contains "lineage" on LineageOS
     */
    private String detectVariant() {
        String productName = getSystemProperty("ro.product.name").toLowerCase();
        String model       = Build.MODEL;

        boolean isLineage = productName.startsWith("lineage_")
                || Build.TAGS.contains("test-keys")
                || Build.DISPLAY.toLowerCase().contains("lineage");

        if (isLineage) {
            // Differentiate lineage by model/product name
            if (model.equalsIgnoreCase(MODEL_FORD) || productName.contains("ford")) {
                return "ford_lineage";
            } else {
                return "austin_lineage";  // default lineage fallback
            }
        }

        if (model.equalsIgnoreCase(MODEL_FORD))   return "ford";
        if (model.equalsIgnoreCase(MODEL_AUSTIN)) return "austin";

        return "austin";
    }

    private String getSystemProperty(String key) {
        try {
            Process p = Runtime.getRuntime().exec("getprop " + key);
            String value = new BufferedReader(
                    new InputStreamReader(p.getInputStream())).readLine();
            return value != null ? value : "";
        } catch (IOException e) {
            return "";
        }
    }

    private void runExploit() {
        if (currentProcess != null) {
            currentProcess.destroy();
            currentProcess = null;
            processStdin = null;
        }

        btnExploit.setEnabled(false);
        setInputEnabled(false);
        tvOutput.setText("");
        appendOutput("[ ] Extracting binary...\n");

        String assetName = "exploit";

        new Thread(() -> {
            File bin;
            try {
                bin = extractAsset(assetName);
            } catch (IOException e) {
                appendOutputAsync("[-] Failed to extract: " + e.getMessage() + "\n");
                runOnUiThread(() -> btnExploit.setEnabled(true));
                return;
            }

            appendOutputAsync("[+] Extracted: " + bin.getAbsolutePath() + "\n");
            appendOutputAsync("[ ] Launching exploit...\n\n");

            try {
                currentProcess = new ProcessBuilder(bin.getAbsolutePath(), detectedGen)
                        .redirectErrorStream(true)
                        .start();

                processStdin = currentProcess.getOutputStream();

                runOnUiThread(() -> setInputEnabled(true));

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(currentProcess.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    appendOutputAsync(line + "\n");
                    if (line.contains("We got root!")) {
                        runOnUiThread(() -> tvPrompt.setText("# "));
                    }
                }

                int code = currentProcess.waitFor();
                appendOutputAsync("\n[*] Process exited (" + code + ")\n");

            } catch (IOException | InterruptedException e) {
                appendOutputAsync("\n[-] Error: " + e.getMessage() + "\n");
            } finally {
                processStdin = null;
                currentProcess = null;
                runOnUiThread(() -> {
                    setInputEnabled(false);
                    btnExploit.setEnabled(true);
                    tvPrompt.setText("$ ");
                });
            }
        }).start();
    }

    private void sendInput() {
        String text = etInput.getText().toString();
        etInput.setText("");

        if (processStdin == null) return;

        appendOutput(text + "\n");

        new Thread(() -> {
            try {
                processStdin.write((text + "\n").getBytes());
                processStdin.flush();
            } catch (IOException e) {
                appendOutputAsync("[-] stdin write error: " + e.getMessage() + "\n");
            }
        }).start();
    }

    private void setInputEnabled(boolean enabled) {
        etInput.setEnabled(enabled);
        btnSend.setEnabled(enabled);
        if (enabled) etInput.requestFocus();
    }

    private File extractAsset(String name) throws IOException {
        File out = new File(getFilesDir(), name);
        try (InputStream in  = getAssets().open(name);
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
        }
        if (!out.setExecutable(true, false))
            throw new IOException("chmod +x failed");
        return out;
    }

    private void appendOutput(String text) {
        tvOutput.append(text);
        scrollOutput.post(() -> scrollOutput.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void appendOutputAsync(String text) {
        runOnUiThread(() -> appendOutput(text));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentProcess != null) currentProcess.destroy();
    }
}