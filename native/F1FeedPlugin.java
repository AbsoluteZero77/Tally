package com.tally.sheet;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

@CapacitorPlugin(name = "F1Feed")
public class F1FeedPlugin extends Plugin {
    private static final String BASE = "livetiming.formula1.com/signalr";
    private static final String HUB = "[{\"name\":\"Streaming\"}]";
    private static final String UA = "BestHTTP";
    private static final String SUB =
        "{\"H\":\"Streaming\",\"M\":\"Subscribe\",\"A\":[[\"Heartbeat\",\"SessionInfo\",\"DriverList\","
        + "\"TimingData\",\"TimingAppData\",\"LapCount\",\"TrackStatus\",\"RaceControlMessages\"]],\"I\":1}";

    private final OkHttpClient client = new OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build();
    private WebSocket ws;

    private void status(String s) {
        JSObject o = new JSObject();
        o.put("s", s);
        notifyListeners("status", o);
    }

    @PluginMethod
    public void connect(final PluginCall call) {
        new Thread(() -> {
            try {
                closeSocket();
                String hub = URLEncoder.encode(HUB, "UTF-8");
                Request neg = new Request.Builder()
                    .url("https://" + BASE + "/negotiate?connectionData=" + hub + "&clientProtocol=1.5")
                    .header("User-Agent", UA)
                    .build();
                String token;
                StringBuilder cookie = new StringBuilder();
                try (Response r = client.newCall(neg).execute()) {
                    if (!r.isSuccessful()) throw new Exception("negotiate " + r.code());
                    List<String> sc = r.headers("Set-Cookie");
                    for (String c : sc) {
                        if (cookie.length() > 0) cookie.append("; ");
                        cookie.append(c.split(";")[0]);
                    }
                    token = new JSONObject(r.body().string()).getString("ConnectionToken");
                }
                Request req = new Request.Builder()
                    .url("wss://" + BASE + "/connect?clientProtocol=1.5&transport=webSockets&connectionToken="
                        + URLEncoder.encode(token, "UTF-8") + "&connectionData=" + hub)
                    .header("User-Agent", UA)
                    .header("Cookie", cookie.toString())
                    .build();
                ws = client.newWebSocket(req, new WebSocketListener() {
                    @Override public void onOpen(WebSocket w, Response r) {
                        w.send(SUB);
                        status("open");
                    }
                    @Override public void onMessage(WebSocket w, String text) {
                        JSObject o = new JSObject();
                        o.put("d", text);
                        notifyListeners("msg", o);
                    }
                    @Override public void onClosed(WebSocket w, int code, String reason) {
                        status("closed");
                    }
                    @Override public void onFailure(WebSocket w, Throwable t, Response r) {
                        status("failed: " + (r != null ? r.code() + " " : "") + t.getMessage());
                    }
                });
                call.resolve();
            } catch (Exception e) {
                status("failed: " + e.getMessage());
                call.reject(String.valueOf(e.getMessage()));
            }
        }).start();
    }

    private void closeSocket() {
        if (ws != null) {
            ws.cancel();
            ws = null;
        }
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        closeSocket();
        call.resolve();
    }
}
