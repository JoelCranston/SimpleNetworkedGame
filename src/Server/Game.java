/*
 * Networked Game
 * Copyright (c) 2014, Joel Cranston. All rights reserved.
 */

package Server;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds a game instance.
 * @author joel
 */
public class Game {
    //Constants
    static final int MAXPLAYERS = 9;
    static final Integer MAX_HP = 9;    
    //static private final PrintStream debug = System.out;
    static private final PrintStream error = System.out;
    //Messages
    static final String START_OF_GAME = "B";
    static final String START_OF_TURN = "T"; 
    static final String END_OF_GAME_WIN = "O1";
    static final String END_OF_GAME_LOSE = "O0";
    static final String END_OF_TURN = "E";

    //Game state info
    final Integer hp;//starting hit points
    final String name;
    AtomicInteger state; //game states  
                         // 1: no clients yet.
                         // 2: waitig for additional players
                         // 3: all clients connected, wait for start loc.
                         // 4: waiting for turn actions.
                         // 5: game over.
    int nPlayers;   // number of players needed to start game.
    int currentPlayers = 0;    //needs to be protected
    final int damagePerHit;
    final Boolean scanNearShip;
    final int scanDistance;
    AtomicBoolean ready; //used to signal that all clients have finished a required action.
    //Database
    Client[] clients;


    /**
     * Constructor for a game instance.
     * @param name is the games name
     * @param players is the number of players
     * @param hp is the starting hit points for each player (0-9)
     * @param scanDist is the distance from a point that enemies will be detected.
     * @param hitDmg is the number of hit points removed for each hit.
     * @param scanNear is true if players always scan around their location.
     */
    public Game(String name, int players, int hp, int scanDist, int hitDmg, boolean scanNear) {
        this.name = name;
        state = new AtomicInteger(1);
        ready = new AtomicBoolean(false);
        if(players > 1 && players < MAXPLAYERS)
            this.nPlayers = players;
        else
            this.nPlayers = 2;
        if(hp <= 9)//max of one digit.
            this.hp = hp;
        else 
            this.hp = MAX_HP;
        //create a array to hold the client/socket pairs.
        this.clients = new Client[nPlayers];        
        //optional rules.
        this.scanNearShip = scanNear;
        this.damagePerHit = hitDmg;
        this.scanDistance = scanDist;
            
        }
    
    //Stores info about each client 
    private static class Client {
        String username;
        //java.net.Socket socket;
        AtomicInteger hp;   // hp is stored and sent as number of hits survivable, -1 is dead. 
                            // this must be atomic, as multiple ClientThreads could update it.
        PrintWriter out;    // output stream to client.
        Boolean wasHit = false;
        Pair position;
        Pair[] scanHits;
        AtomicBoolean moved;
        public Client(String name, Socket s, Integer hp) {
            this.username = name;
            //this.socket = s;
            this.hp = new AtomicInteger(hp);
            this.moved = new AtomicBoolean(false);
            try{
                this.out = new PrintWriter(s.getOutputStream(), true);
            }catch(IOException e){
                error.println("exception seting up printwriter in Client.");
            }
        }
    }
    
    //Just a set of comparable x,y cordinates
    private static class Pair implements Comparable<Pair>{
        Integer x;
        Integer y;
        public Pair(Integer x, Integer y){
            this.x = x;
            this.y = y;
        }
        @Override
        public int compareTo(Pair other){
            int last = this.x.compareTo(other.x);
            return last == 0 ? this.y.compareTo(other.y) : last;
        }
    }
    
    /**
     * Adds a client to the game.
     * @param name is the clients username.
     * @param s is the clients connection socket.
     */
    public synchronized void addclient(String name, Socket s){
        state.compareAndSet(1, 2);
        if(!ready.get()){
            clients[currentPlayers++] = new Client(name,s,hp);
            if(currentPlayers == nPlayers){//we have enough players
                ready.set(true);//ready to sendSOG
                state.set(3);//waiting for start positions.
            }
        }else //log error
            error.println("Someone tried to add a client '" + name + "' to a full game.");
    }

    /**
     * Sends start of game message to all clients if game is ready.
     * 
     */
    public void sendSOG(){     
        if(ready.get()){ //we have enough players
            StringBuilder players;            
            for(int i = 0; i < clients.length; i++){
                //find the names of the other players
                int playersAdded = 0;
                players = new StringBuilder();
                for(int j = 0; j < clients.length;j++){
                    if(i != j){// all usernames except current users
                        players.append(clients[j].username);
                        if(++playersAdded < nPlayers - 1)
                            players.append(':');         
                    }         
                }//send the SOG message
                clients[i].out.printf("%s%s\n",START_OF_GAME,players.toString());  
            }
            state.set(3);//waiting for startLocations
            ready.set(false);
        }else //log error
            error.println("ERROR: Someone tried to start game '" + name + "'before it was ready");         
    }
    
    /**
     * Send the clients a StartOfTurn message if they are still alive.
     * Removes eliminated players from the game.
     * only valid in (state 4)
     */
    public synchronized void sendSOT(){
        if(ready.get()){//all clients have responded.
            //Remove elimenated clients
            for(Client i: clients){
                if (i.hp.get() < 0){
                    i.out.printf("%s\n",END_OF_GAME_LOSE);
                    removeClient(i.username);
                }
            }
            
            if(clients.length > 1){//send remaining clients a start of turn message.
                for(Client i: clients){
                    i.moved.set(false);//reset moved flag.
                    i.out.printf("%s%d\n",START_OF_TURN,i.hp.get());
                }
                ready.set(false);//wait for all players to move.
                
            }else{ 
                if(clients.length == 1){//only one remaining, so send End of Game
                    clients[0].out.printf("%s\n",END_OF_GAME_WIN);
                    removeClient(clients[0].username);
                }
            state.set(5);
            }
        }                
    }

    /**
     * Sends a end of turn (EOT) message to all of the players
     */
        public synchronized void sendEOT(){
        Integer numScanHits; 
        Set<Pair> hits;
        for(Client i: clients){
            if(i.scanHits == null)//no scan action took place
                if(scanNearShip){
                    hits = scanPos(i.username, i.position.x, i.position.y);
                    i.scanHits = hits.toArray(new Pair[hits.size()]);
                    hits.clear();
                }
            numScanHits = i.scanHits.length;
            //Send Message
            i.out.printf("%s%d%d",END_OF_TURN,(i.wasHit ? 1 : 0),numScanHits);
            if(numScanHits != 0){   //add each x,y pair.
                for(Pair j: i.scanHits){
                    i.out.printf("%d%d",j.x,j.y);
                }    
            } 
            i.out.print('\n');
            i.wasHit = false;// reset hit indicator.
            i.scanHits = null;//clear any hit detected last turn 
            
        }
        ready.set(true);//ready for start of turn.
    }//end sendEOT
    
    /**
     * Sets the players start location
     * @param name is the username of the player
     * @param x is the x coordinate of the target
     * @param y is the y coordinate of the target
     */
        public void setStart(String name, Integer x, Integer y){
        Boolean unready = false;
        for(Client i: clients){           
            if(name.equals(i.username))
                i.position = new Pair(x,y);
            if(i.position == null)
                unready = true;//at least one player has not set their start loc.
        }
        if(!unready)
            ready.set(true);//all start locations were set.
    }
    
    /**
     * Performs the move action
     * @param name is the username of the player
     * @param dir is the direction to move, (numeric keypad)
     */
        public void move(String name, Integer dir){
        for(Client i: clients){
            if(name.equals(i.username)){
                switch (dir){//set x cord 
                    case 1://fall through to 7
                    case 4:
                    case 7:
                        if(i.position.x > 0)//don't move off board
                            i.position.x -= 1;
                        break;
                    case 3://fall through to 9
                    case 6:
                    case 9:
                        if(i.position.x < 9)//don't move off board
                            i.position.x += 1;
                        break;
                    default:
                        //do nothing     
                }
                switch (dir){// set y cord
                    case 1:
                    case 2:
                    case 3:
                        if(i.position.y < 9)
                            i.position.y += 1;
                        break;
                    case 7:
                    case 8:
                    case 9:
                        if(i.position.y > 0)
                            i.position.y -= 1;
                        break;
                    default:
                        //do nothing     
                }
                i.moved.set(true);
            }
                 
        }//end for
        //check if all players have moved.
        checkAllMoved();
    }//end movePlayer
    
    /**
     * Perform the Scan action.
     * @param name is the username of the player
     * @param x is the x coordinate of the target
     * @param y is the y coordinate of the target
     */
    public void scan(String name, Integer x, Integer y){
        Set<Pair> hits = null;
        for(Client i: clients){      
            if(i.username.equals(name)){
                hits = scanPos(i.username,x,y);//scan at target
                if(scanNearShip)//server|game specific rule.
                    hits.addAll(scanPos(i.username,i.position.x,i.position.y));  //scan arround ship
                i.scanHits = hits.toArray(new Pair[hits.size()]);
                i.moved.set(true);
            }    
        }
        if(hits != null)//cleanup the set
            hits.clear(); 
        checkAllMoved();
    }//end scan
    
    /**
     * Perform the fire action.
     * @param name is the username of the player
     * @param x is the x coordinate of the target
     * @param y is the y coordinate of the target
     */
    public void fire(String name, Integer x, Integer y){
        Client player = null;
        Boolean hitScored = false;
        for(Client i: clients){      
            if(i.username.equals(name)){
                player = i;
                i.moved.set(true);
                //don't fire on yourself
            }else
                if(i.position.x.equals(x) && i.position.y.equals(y))//check to see if there is a ship there
                    if(calcHit(i.position,new Pair(x,y))){//check to see if it hit.
                        i.hp.addAndGet(-damagePerHit);//subtract hit points from the player.
                        hitScored = true;
                    }
            
        }
        
        if(hitScored && player != null)//inform the player whether they hit or not.
            player.wasHit = true; 
        checkAllMoved();
    }

    /**
     * Removes a disconnecting client from the game
     * @param name is the username of the client
     */
    
    //why am i here for end of game
    public synchronized void clientDisconnected(String name){
        if(clientIsPresent(name)){// make sure client is actualy in the game
            if(currentPlayers  < 3){//game had only 2 players
                if(state.get() > 2){//game had already started
                    //send players an EOG message.
                    state.set(5);//game over...
                    for(Client i: clients)
                        if(!i.username.equals(name)){
                            i.out.printf("%s\n",END_OF_GAME_WIN);
                            removeClient(i.username);
                        }else 
                            i.out.printf("%s\n",END_OF_GAME_LOSE);
                            //allways gets removed below 
                            
                }//else not started yet so just remove client.
            }//else, enough players to continue so just remove the client
            removeClient(name);
        }//else do nothing
          
    }
    //checks for players around a specific position.
    private Set<Pair> scanPos(String name, Integer x, Integer y){
        Set<Pair> hits = new TreeSet ();//set should prevent duplicates.
        for(Client i: clients){
            if(i.username.equals(name))//don't want to detect yourself.
                continue;
            if(i.position.x <= x + scanDistance && i.position.x >= x - scanDistance)
                if(i.position.y <= y + scanDistance && i.position.y >= y - scanDistance)
                    hits.add(i.position);  
        }
        return hits;
    }
    private Boolean calcHit(Pair a, Pair b){
        //int hitProbability  = 100 - (distance from pair a to pair b) * 10 //possible hit formula
        //return = (hitProbability > (random 1-100) ? true : false)

        return true; //hit formula not implemented yet.
    }
    private synchronized void checkAllMoved(){
        Boolean unready = false;
        for(Client i: clients)
            if(!i.moved.get()){// if any player has not moved flag unready
                unready = true;
                //debug.println("DEBUG "+ i.username + ": move flag is = " + i.moved.get());
            }
        if(!unready)//if all have moved set ready to true so we can end turn.
            ready.set(true);
    }
    
    /**
     * Removes the named client and shrinks the size of client array
     * @param name is the username of the player
     */    
    synchronized void removeClient(String name){  
        if(currentPlayers > 0){
            if(clientIsPresent(name)){
                //=int len = clients.length;
                Client[] temp = clients;
                //need to reduce the number of players if the game is in progress
                if(state.get() > 2)
                   nPlayers--; 

                clients = new Client[nPlayers];

                //copy all but named player to new Client array
                int i = 0;
                int j = 0;
                while( i < currentPlayers){
                    if(temp[i].username.equals(name)){
                        //temp[i] = null;
                        i++;
                    }else{
                        clients[j] = temp[i];
                        i++;
                        j++;                
                    }  
                }
                currentPlayers--;
            }
                    
        }else//0 players == everyone died that turn?.
            clients = new Client[0];
    }//end removeClient
    private boolean clientIsPresent(String name){
        for(Client i: clients){
            if(i.username.equals(name))
                    return true;
        }
        return false;
    }
    private void sendEOG(){
        for(Client i: clients){
            if(i.hp.get() >= 0)
                i.out.printf("%s\n",END_OF_GAME_WIN);
            else
                i.out.printf("%s\n",END_OF_GAME_LOSE);
        }
    }
}
