/*
 * Server for networked game
 * Copyright (c) 2014, Joel Cranston. All rights reserved.
 */
package Server;

import static Server.GameServer.clientList;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author joel
 */
public class GameServer {
    //Consts
    static final int CONN_TIMEOUT = 30000;// (30 seconds)
    static final int SOCKET_TIMEOUT= 2000;
    static final String ADMIN = "admin$"; // name of admin account.
    //Default Rules, (Rules to be set by config file or admin consol.)
    static final int HP = 1;
    static final int PLAYERS = 4;
    static final Boolean SCAN_NEAR_SHIP = true;
    static final int HIT_DAMAGE = 1;    
    static final int SCAN_DISTANCE = 1;
    //Vars
    static boolean shutdown = false;
    static Map<String,Game> gameList;     //all games waiting for players
    static List<ClientThread> clientList; //all connected clients
    static List<String> userList;         //all current usernames
    static Map<String,Game> activeGames;  //all currently active(started) games
    static final String welcomeMsg = 
              "Welcome to the test server:"
            + "Server Rules>:"
            + "\tPlayers start with "+ (HP+1) +" hit points.:"
            + "\tGames require "+ PLAYERS + " players to start.:"
            + (SCAN_NEAR_SHIP?"\tYou always scan your current location.:":"")
            + "\tChance to hit is 100%:"
            + "\tEach Hit does " + HIT_DAMAGE + " hit point of damage.";

    /**
     * The game server
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        int portNumber = 9001;
        if(args.length == 1)
            portNumber = Integer.parseInt(args[0]);
            
        
        gameList = new ConcurrentHashMap();
        clientList = Collections.synchronizedList(new ArrayList());
        userList = Collections.synchronizedList(new ArrayList());
        activeGames = new ConcurrentHashMap();
        
        //Try with resources 
        try (ServerSocket serverSocket = new ServerSocket(portNumber)){
            serverSocket.setSoTimeout(SOCKET_TIMEOUT);
            while(!shutdown){
                try {
                    Socket p = serverSocket.accept(); //will wait until socket timeout.
                    ClientThread c = new ClientThread(p);
                    new Thread(c).start();
                    clientList.add(c);
                
                }catch (SocketTimeoutException e) { 
                    //will check the timer on all threads.
                    try{
                        for (ClientThread client : clientList) {
                          client.updateTimer();
                        }
                    }catch (ConcurrentModificationException ce){
                        //ignore, a client was removed while we were in the list.
                    }
                }           
            }
            
        }catch (IOException e){
            //Logger.getLogger(GameServer.class.getName()).log(Level.SEVERE, null, e);
            System.out.println("IO Exception in GameServer ");
            System.exit(1);
        }
            
        System.out.println("Connection server shutdown, waiting for threads to finish.");
    }
    
}
  