/*
 * Connection Timer for Text based Game Client
 * Copyright (c) 2014, Joel Cranston. All rights reserved.
 */

package TextClient;

import java.util.TimerTask;

/**
 * This Task just checks the heartbeat timer 
 * @author joel
 */

public class ServerResponceTimer extends TimerTask {
    GameClient client;
    ServerResponceTimer(GameClient i){
        client = i;
    }
    @Override
    public void run() {
        client.checkConnection();
    }
    
}
