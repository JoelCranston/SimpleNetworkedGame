/*
 * Thread for TCP connection to a client.
 * Copyright (c) 2014, Joel Cranston. All rights reserved.
 */

package Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * @author joel
 */
class ClientThread implements Runnable {
    Socket client;
    
    //incoming message headers
    static final char LOGIN = 'L';
    static final char NEWGAME = 'N';
    static final char CONNECT = 'C';
    static final char START = 'S';
    static final char FIRE = 'F'; 
    static final char SCAN = 'P';
    static final char MOVE = 'M';
    static final char HEARTBEAT = 'H';
    static final char QUIT = 'Q'; 
    
    //Outgoing Messages
    static final char WELCOME = 'W';
    static final String NEWGAME_JOIN = "N0";
    static final String NEWGAME_CREATE = "N1";
    static final String NEWGAME_ERROR ="N2";
    static final String AVAILABLE_GAMES = "A";
    static final String LOGIN_ERROR = "X1";

    private Game game;                  //the clients game           
    private String gamename = null;     //name of game session
    private String username = null;     //login name of client
    private Integer state = 0;  //state 0 = waiting for login
                                //state 1 = waiting for connection to game
                                //state 2 = waiting for another player
                                //state 3 = waiting for start location
                                //state 4 = waiting for turn action
                                //state 5 = game over
    private PrintWriter out;
    private BufferedReader in;
    private final PrintStream debug = System.out;
    private final PrintStream error = System.out;
    
    private final Integer ConnectionTimeout = GameServer.CONN_TIMEOUT;
    private Long timeOfLastMessage;
    private Long timeLeft;
    private String heartbeatMessage; //string sent to client.
    private Boolean shutdown;
    
    public ClientThread(Socket accept){
        this.client = accept;
        this.timeOfLastMessage = System.currentTimeMillis();
        this.shutdown = false;
    }
    
    @Override
    public void run(){
        try(PrintWriter outStream = new PrintWriter(client.getOutputStream(), true);                   
            BufferedReader inStream = new BufferedReader(new InputStreamReader(client.getInputStream()))
            ){
            this.in = inStream;
            this.out = outStream;
            runGame();
            closeConnection();       
        }catch (IOException e){
            if(shutdown){//connection was closed, quietly exit.
                //debug.println("IOException on shutdown in clientThread.");   
                return;
            }
            //Logger.getLogger(ClientThread.class.getName()).log(Level.SEVERE, null, e);
            error.println("IOException in clientThread, closing thread.");
        }finally{
            // make sure this client is removed if from database if this tread exits.
            GameServer.clientList.remove(this); 
        }
        
    
    }
    private void runGame() throws IOException{ 
        String inputLine;    
        // send welcome message
        out.printf("%c%s\n",WELCOME,GameServer.welcomeMsg);
        while((inputLine = in.readLine())!=null){ 
            
            //debug.println("DEBUG " + username + ": message recieved = "+ inputLine);
            //debug.println("DEBUG " + username + ": Starting state = "+ state);
            //ignore blank messages
            if(inputLine.length() == 0)
                continue;
            
            resetTimer();//reset on every valid message
            //Client sent quit message.
            if(inputLine.charAt(0) == QUIT){
                state = 5;
            }
           
            //Deal with heartbeat messages
            if(inputLine.charAt(0) == HEARTBEAT ){    
                if(inputLine.substring(1).equals(heartbeatMessage)){
                    //client was responding to out message.    
                }else{
                    //client is  requesting a responce.
                    sendHeartbeat(inputLine.substring(1));
                }      
            }
            //reset the connection timer.
            //all other Messages
            switch (state) {
                case 0://wait for login
                    if(inputLine.charAt(0) == LOGIN){
                        login(inputLine.substring(1));
                    }
                case 1://wait for connection
                    if(inputLine.charAt(0) == CONNECT ){
                        connectToGame(inputLine.substring(1));      
                    }else           
                    break;   
                case 2://waiting for more player
                    //Do not break here, fallthrough to 3 in case it a start message was recieved while in state 2.
                case 3://wait for starting location
                    if(inputLine.charAt(0) == START ){
                          startLocation(inputLine.substring(1));
                    }
                    break;
                case 4://wait for action
                    if(inputLine.charAt(0) == MOVE){
                        move(inputLine.substring(1));
                    }
                    if(inputLine.charAt(0) == FIRE){
                        fire(inputLine.substring(1));
                    }
                    if(inputLine.charAt(0) == SCAN){
                        scan(inputLine.substring(1));
                    }
                    //if all players have moved.
                    if(game.ready.get()){
                        game.sendEOT();//end the turns
                        game.sendSOT();//start next turn 
                    }   
                    break;
                case 5://game over, Client sent quit message.
                    debug.printf("%s has logged out\n",username);
                    //debug.println("DEBUG " + username + ": Server thread exited"); 
                    return;
                case 9:
                    //admin consol
                    adminConsol(inputLine);
                default: 
                    //debug.println("DEBUG " + username + ": Invalid state: " + inputLine);
            }//end switch (state)
            //debug.println("DEBUG " + username + ": Ending state = " + state);
        }//end while
        
        //We get here when client closes the connection.
        //debug.println("DEBUG " + username + ": Client Unexpectedly closed the connection");
        //debug.println("DEBUG " + username + ": Final state = " + state);    
    }  
    private void login(String message){
            
            username = message;
            //check to see if it is a admin
            if(username.equals(GameServer.ADMIN)){
                state = 9;
                return;
            }
            if(GameServer.userList.contains(username)){
                error.println("ERROR: username " + username + " allready in use");
                out.printf("%s\n",LOGIN_ERROR);
                username = null;
                return;
            }
            GameServer.userList.add(username);
            debug.printf("New user %s logged in\n",username);
            sendGameList();//send client gamelist.
            state = 1;      
    }
    private void connectToGame(String message){  
        gamename = message;
        //Make sure its not a currently active gameName
        if(GameServer.activeGames.containsKey(gamename)){
            out.printf("%s\n",NEWGAME_ERROR);
            return;
        }
        //Check to see if gameName is in gamelist
        if(GameServer.gameList.containsKey(gamename)){
            //if gameName is in gamelist 
            game = GameServer.gameList.get(gamename);
            game.addclient(username, client);
            out.printf("%s\n",NEWGAME_JOIN);
            //see if we still need more players
            if(game.ready.get()){
                state = 3;//game is ready
                //Move game to Active games list
                GameServer.activeGames.put(gamename, game);
                GameServer.gameList.remove(gamename);
                //Send newGame message to all clients in game.
                game.sendSOG();
                debug.printf("Game (%s) is starting\n",gamename);
            }else{
                state = 2;//need more players
                debug.printf("%s has joined (%s)\n",username,gamename);
            }
        }else{
            //create a new game,register it in gamelist 
            game = new Game(gamename, GameServer.PLAYERS, GameServer.HP, 
                    GameServer.SCAN_DISTANCE, GameServer.HIT_DAMAGE, GameServer.SCAN_NEAR_SHIP);
            GameServer.gameList.put(gamename, game);
            game.addclient(username, client);
            state = 2;
            resendGameList();
            //send newGame message
            out.printf("%s\n",NEWGAME_CREATE);
            debug.printf("%s Created a new game (%s)\n",username,gamename);                            
        }
    }
    private void startLocation(String message){
        Integer[] coordinates = getCoordinates(message.charAt(0),message.charAt(1));
        if(coordinates != null){
            game.setStart(username,coordinates[0],coordinates[1]);
            state = 4;
        }else{
            error.println("ERROR: invalid cordinates: " + message);
        }
        if(game.ready.get()){
            
            game.sendSOT(); //all players have set their start location
                            //ready to start turns.
            resendGameList();//remove it from avalible games
        }
    }
    private void move(String dir){
        Integer moveDir = Integer.parseInt(dir);
        if(moveDir > 0 && moveDir < 10 && moveDir != 5)//0,5 are invalid.
            game.move(username, moveDir);
        else
            error.println("ERROR: invalid move direction: " + dir);
    }
    private void fire(String target){
        Integer[] coordinates = getCoordinates(target.charAt(0),target.charAt(1));
        if(coordinates != null){
            game.fire(username, coordinates[0], coordinates[1]);

        }else
             error.println("ERROR: invalid cordinates: " + target);
    }
    private void scan(String target){
        Integer[] cords = getCoordinates(target.charAt(0),target.charAt(1));
        if(cords != null){
            game.scan(username, cords[0], cords[1]);      
        }else
             error.println("ERROR: invalid cordinates: " + target);        
    }
    //Removes the user and game from the databases
    private synchronized void closeConnection() throws IOException{
        if(game != null){
            game.clientDisconnected(username);//removes client from the game, and ends it if it can not continue.
            //if nobody is connected remove the game from the databases
            if(game.currentPlayers == 0){ //after removing our client
                GameServer.gameList.remove(gamename);
                GameServer.activeGames.remove(gamename); 
                resendGameList();
                debug.printf("Removing (%s)\n",gamename);
            }       
        }
        //GameServer.clientList.remove(this); 
        GameServer.userList.remove(username);
        //debug.println("DEBUG: removing " + username + " from user database");
        client.close();
    }
    private void sendGameList(){
        StringBuilder games = new StringBuilder(AVAILABLE_GAMES);
        //System.out.println(games.toString());
        for(String i:GameServer.gameList.keySet()){
            games.append(i);
            games.append(':');    
        }
        if(games.length() > 1)
            games.deleteCharAt(games.length()-1);//remove last colon
        ///System.out.println(games.toString());
        out.printf("%s\n",games.toString());
            
    }
    //sends game list to all users in state 1
    private void resendGameList(){
        for(ClientThread c: GameServer.clientList)
            if (c.state == 1)
                c.sendGameList();            
    }
    private Integer[] getCoordinates(char a, char b){
        Integer x = Integer.parseInt(new Character(a).toString());
        Integer y = Integer.parseInt(new Character(b).toString());
        if(x <= 9 && x >= 0 && y <= 9 && y >= 0){
            return new Integer[]{x,y};
        }
        else
            return null;       
    }
    //check the timer and send a heartbeat message to the client if needed
    //this must be run by a non-blocking thread.
    void updateTimer(){           
        Long time = System.currentTimeMillis();
        timeLeft = ConnectionTimeout - (time - timeOfLastMessage); //timeout - (elapsed time)
        
        //send heartbeat when less then 10 seconds is remaining before timeout.
        if ( timeLeft < 10000){
            heartbeatMessage = username + time.toString();
            sendHeartbeat(heartbeatMessage);
            //debug.printf("DEBUG: sending %s a HB message: %s\n",username,heartbeatMessage);
        } 
        
        //disconnect non-responding clients.
        if(timeLeft < 0){
            try{
                debug.printf("Disconnected user %s, for inactivity\n", username);
                shutdown = true;                
                closeConnection();
                in.close(); // clientThread is blocked on read, so close the buffered reader. 
                            //This will cause it to exit with an ioexceptionx
            }catch (IOException e){
                //debug.println("IOException on closeConnection in updateTimer.");
            }     
        }
        //debug.printf("DEBUG: %s updating timer, %d milliseconds remaining\n",username,timeLeft);
    }
    //Sets time of last message to current time.
    void resetTimer(){
        //debug.printf("DEBUG %s reseting timer\n",username);
        timeOfLastMessage = System.currentTimeMillis();
        //timeLeft = ConnectionTimeout.longValue();
    }      
    void sendHeartbeat(String hbMessage){
        out.printf("%c%s\n",HEARTBEAT,hbMessage);
    }
    
    //special operations for administration client
    private void adminConsol(String message) {    
        if(message.equals("Shutdown"))
            GameServer.shutdown = true;
    }
}
