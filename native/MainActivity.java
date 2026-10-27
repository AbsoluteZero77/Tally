package com.tally.sheet;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(F1FeedPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
