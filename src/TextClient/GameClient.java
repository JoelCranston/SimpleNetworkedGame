/*
 * The MIT License
 *
 * Copyright 2015 Joel Cranston.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package TextClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

/** 
 * Text based Game Client
 * @author joel
 */
public class GameClient {
    //message headers
    private static final String STRING_SEPARATOR = ":";
    private static final int HEARTBEAT_INTERVAL = 10000; 
    private static final long CONNECTION_TIMEOUT = 30000;
    
    //incoming server messages
    private static final char WELCOME = 'W';
    private static final char NEW_GAME = 'N';
    private static final char AVAILABLE_GAMES = 'A';
    private static final char START_OF_GAME = 'B';
    private static final char START_OF_TURN = 'T'; 
    private static final char END_OF_GAME = 'O';
    private static final char END_OF_TURN = 'E';
    private static final char HEARTBEAT = 'H';
    private static final char ERROR = 'X';

    //Program control
    private static Boolean shutdown = false;
    private static Boolean startup = true;
  
    //IO
    private final PrintStream out = System.out;
    //private final PrintStream debug = System.out;
    private final MessageSender sender;
    private final BufferedReader stdIn;
    private final BufferedReader in;
    
    //Gamestate    
    private Integer state;
    private String username;
    private String gamename;
    private String turnAction; //F|S|M
    private Integer targetCoordinates[];
    private Integer moveDir;
    private Integer hitpoints;
    private Integer[] location;
    
    //Timers
    private String heartbeatMessage;//last heartbeat sent to server
    private static Timer heartbeatTimer;
    private long timeOfLastMessage;
    private TimerTask heartbeatTask; 

    
    public GameClient(PrintWriter out, BufferedReader stdIn, BufferedReader in){
        this.state = 0;
        this.sender = new MessageSender(out); 
        this.stdIn = stdIn;
        this.in = in;
        this.hitpoints = 0;
        this.location = new Integer[2]; 
        this.username = null;
        this.targetCoordinates = new Integer[2];

    }
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        String hostName = "127.0.0.1";
        int portNumber = 9001;
        if(args.length == 2){
            portNumber = Integer.parseInt(args[1]);
            hostName = args[0];
        }
        
        try (//Try with resources so we auto close resources
            //Try with resources so we auto close resources
            Socket serverConnection = new Socket(hostName, portNumber);
            //get sockets output stream and open a printwriter on it 
            PrintWriter SocketOut = new PrintWriter(serverConnection.getOutputStream(), true); 
            //get sockets input stream and open a buffered reader on it
            BufferedReader in = new BufferedReader(new InputStreamReader(serverConnection.getInputStream()));
            //create a bufferedreader for stdIn
            BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
            ){ //end of resources block
            
            GameClient client = new GameClient(SocketOut,stdIn,in);
            startup = false;//end of startup stage
            client.setTimer();
            while(!shutdown && !serverConnection.isClosed()){
                if(stdIn.ready()){
                    client.getUserInput();
                }   
                if(in.ready())//if there is a message in the buffer
                    client.readMessage();
                Thread.sleep(500);
            }
    
        } catch (InterruptedException e){
            if(shutdown)//quietly exit if shutdown was requested.
                System.exit(1);
            System.out.println("GameClient main loop interupted");
            System.out.println("Exception: "+e.getMessage()+" Thrown by: "+e.getClass().getSimpleName());
        } catch (IOException e){
            if(shutdown == true)//quietly exit if shutdown is expected
                System.exit(1);
            if(startup == true){//alert user that the connection was unsuccessful
                System.out.println("Could not connect to "+hostName+" on port "+ portNumber);
                System.exit(1);
            }
            System.out.println("IOException in GameClient");
            e.getCause().printStackTrace();
            System.exit(1);
        } finally{
            if(heartbeatTimer != null)
                heartbeatTimer.cancel();
        }
        
    }

    
    
    
    
 /*==============================================================================    
 *
 * Incoming message handler.
 *
 */  
    private void readMessage() throws IOException{
        String message = in.readLine();
//
//        if((message = in.readLine()) == null){ //this does not work
//            debug.println("DEBUG: SOCKET CLOSED");
//            shutdown = true;
//            return;
//        }
        if(message.length() == 0)//ignore blank messages
            return;
        resetTimer();
        switch (message.charAt(0)){
            case WELCOME:
                state = 1;
                printWelcome(message.substring(1));// the welcome message
                break;
            case AVAILABLE_GAMES:
                state = 3;
                gameList(message.substring(1));// list of games
                break;
            case NEW_GAME:
                //check to see if the server sent an error message.
                if(message.substring(1).equals("2")){
                    out.println("Error joining or creating game, Please try another game name.");
                    //request gamlist again =====================================================================TODO
                    state = 3;
                }else{
                    //if no error wait for start of game; 
                    newGame(message.substring(1));//new or existing
                    state = 5;
                } 
                break;
            case START_OF_GAME:
                state = 6;
                startOfGame(message.substring(1)); //other players     
                break;            
            case START_OF_TURN:
                state = 8;
                startOfTurn(message.substring(1)); //hp
                break;
            case END_OF_TURN:
                state = 12;
                endOfTurn(message.substring(1)); //hit scored:scan hits:xy
                break;
            case END_OF_GAME:
                state = 13;
                endOfGame(message.substring(1));//print results of game
                shutdown = true;
                break;           
            case HEARTBEAT:
                heartbeat(message.substring(1));
                break;
            case ERROR:
                if(state == 2){
                    state = 1;
                    out.print(" Username not avalible!\n Enter your login name: ");
                }
                    
        }
    }
    private void printWelcome(String list){
        out.println("\n=========== Successfully Connected To The Server ===========\n");
        String[] lines = list.split(STRING_SEPARATOR);
        if(lines[0].equals(""))
            out.print(" (no welcom message)");
        for(String line: lines)
            out.println(" "+line);
        
        out.println("\n============================================================\n");
        out.print(" Enter your login name: ");
    }
    private void gameList(String list){
        out.println("\n====================== Avalible Games ======================");
        String[] names = list.split(STRING_SEPARATOR);
        if(names[0].equals(""))
            out.print(" (No Games Avalable)");
        for(String name: names)
            out.println(" "+name);        
        out.print(" Enter the name of the game to join or create: ");
    }
    private void startOfGame(String list){
        out.println("=================== The Game Has Started ===================\n"
                  + " Your opponents are:");
        String[] names = list.split(":");
        for(String name: names)
            out.println(" "+name);
        out.print(" Enter your starting locaion [xy]: ");
    }
    private void newGame(String s){
        if(s.equals("0"))
            out.println("======= You have successfuly joined an existing game =======");
        else 
            out.println("=============== You have created a new game ================");
        out.println("        Please wait for the other players to join.\n");
    }
    private void endOfGame(String result){
        if(result.equals("0"))
            result = "LOSE";
        else
            result = "WIN";
                
        out.println("\n==================== The Game Has Ended ====================\n");
        out.println("                          YOU " + result);
        
    }
    private void heartbeat(String message){
        //debug.printf("DEBUG recieving heartbeat message\n");
        if(!message.equals(heartbeatMessage))//{
            sender.hbMsg(message); //server is requesting a responce.
        //}else
            // server replied to our request
    }
    private void endOfTurn(String turnReport){
        out.println("==================== The turn has ended ====================");
        if(turnReport.charAt(0) == '1')
            out.println(" You scored a HIT!");
        Integer scanHits = Integer.parseInt(turnReport.substring(1, 2));
        if(scanHits > 0){
            out.println(" Enemy detected at: ");
            int offset = 2; //location in string of first cordinate
            Integer x,y;
            for(int i = 0; i < scanHits ; i++){
                x = Integer.parseInt(turnReport.substring(offset, ++offset));
                y = Integer.parseInt(turnReport.substring(offset, ++offset));
                out.println(" (" + x.toString() + "," + y.toString() + ")");
            }      
        }
    }
    private void startOfTurn(String hp){
        Integer x = Integer.parseInt(hp) + 1;
        if ((hitpoints - x) > 0 )
            out.println(" You have been HIT!");
        hitpoints = x;
        out.println("=================== The Turn Is Starting ===================\n"
                  + " You have " + hitpoints + " hit point(s) remaining");
        out.print(" Enter your turn action [ F | M | S ]: ");
    }  
    
/*==============================================================================    
 *
 * User Input handler.
 *
 */ 
    private void getUserInput() throws IOException, InterruptedException{
        //boolean noError;
        switch (state){
            case 1://server sent welcome message                
                if(getLoginName()){
                    sender.loginMsg(username);
                    state = 2;
                }
                break;
            case 3://server has sent available games.
                if(getGameName()){
                    sender.connectMsg(gamename);
                    state = 4;
                }
                break;
            case 6://server sent start of game
                if(setStartLocation()){
                    sender.startMsg(location[0],location[1]);
                    state=7;
                }    
                break;
            case 8://server sent start of turn.
                state = getTurnActionType(); //state = {8,9,10,11}
                if(state == 11)
                    sender.genericMessage(turnAction.replace('S', 'P')); 
                break;
            case 9://user Entered fire or scan action; need target      
                if(getCoords()){
                    state = 11;
                    if(turnAction.equals("F"))
                        sender.fireMsg(targetCoordinates[0], targetCoordinates[1]);
                    else
                        sender.scanMsg(targetCoordinates[0], targetCoordinates[1]);
                }
                break;
            case 10://user Entered move action; need direction; 
                if(getMoveDir()){
                    sender.moveMsg(moveDir);
                }
                break;
                
            //Error cases, user typed something without being prompted  
                
            case 2://waiting for available game list.
                //if server did not respond to login message
                //NEED an error message for invalid login name
                getInput();//discard input.
                if(username != null){
                    out.println(" Name unavalible, try another");
                    out.print(" Enter your login name: ");
                    state = 1;
                }
                break;    
            case 4://waiting for responce to game connection
                getInput();//discard input.
                out.println("Waiting for server to respond.");
                break;
            case 5://still waiting for start of game
                getInput();//discard input.
                out.println(" Still waiting for more players. Type q to abort.");
                break;    
            case 7://waiting on start of turn.
            case 11://waiting for other player
                getInput(); //discard input.
                out.println(" Waiting for the other player. Type q to abort the game");
                break;
            
            default://get the unexpected input and discard it. Note Q will result in system exit. 
                getInput();
                //error.println("Unknown State: " + state);
        }//end of switch
    }
    private String getInput() throws IOException, InterruptedException{
        String userInput;
        userInput = stdIn.readLine();
        //allow the user to quit at any time.
        if(userInput.matches("[Qq]|quit")){
            shutdown = true;
            sender.quitMsg();
            throw new InterruptedException("User Requested Shutdown");
        }
        return userInput;
    }
    private boolean getLoginName() throws IOException, InterruptedException{
        username = getInput();

        if(username.matches("\\p{Alnum}+")){
            return true;
        }else{
            out.println("\n Username must be only alpha-numeric characters");
            out.print(" Enter your login name: ");
            return false;
        }

    }
    private boolean getGameName() throws IOException, InterruptedException{
        gamename = getInput();
        if(gamename.matches("\\p{Alnum}+")){
            return true;
        }else{
            out.println(" Name must be only alpha-numeric characters");
            out.print(" Enter the name of the game to join or create: ");
            return false;
        }

    }
    private boolean setStartLocation() throws IOException, InterruptedException{
        String userInput = getInput();

        if(userInput.matches("\\d{2}")){
            location[0]=Integer.parseInt(userInput.substring(0, 1));
            location[1]=Integer.parseInt(userInput.substring(1, 2));
            out.println(" Ok, waiting for other players");
            return true;
        }else{
            out.println(" Each coordinate must be a single digit,[xy]");
            out.print(" Enter your starting locaion [xy]: ");
            return false;
        }

    }
    private Integer getTurnActionType() throws IOException, InterruptedException{

        turnAction = getInput().toUpperCase();
        
        //ok = turnAction.matches("[FfMmSs]");
        //advance user shortcut skips turn turn action value prompt.
        if(turnAction.matches("[FS]\\d{2}|[M][12346789]")){ 
            out.println(" Ok, waiting for other player");
            return 11;
        }
        if(turnAction.matches("[FS]")){         
            out.print(" Enter the target coordinates [xy]: ");
            return 9;   //need coordinates    
        }
        if(turnAction.matches("[M]")){
            out.print(" Enter movement direction using numeric keypad: ");
            return 10; //need direction 
        }
        out.println(" Action must be a single character; F | M | S");
        out.print(" Enter your turn action [ F | M | S ]: ");
        return 8; //error.
           
    } 
    private boolean getCoords() throws IOException, InterruptedException{
        String userInput= getInput();
        if (userInput.matches("\\d{2}")){
            targetCoordinates[0] = Integer.parseInt(userInput.substring(0));
            targetCoordinates[1] = Integer.parseInt(userInput.substring(1));
            out.println(" Ok, waiting for other player");
            return true;
        }else{
            out.println(" Each coordinate must be a single digit");
            out.print(" Enter the target coordinates [xy]: ");
            return false;
        }
    }
    private boolean getMoveDir() throws IOException, InterruptedException{
        String userInput = getInput();
        if(userInput.matches("[12346789]")){
            moveDir = Integer.parseInt(userInput);
            return true;
        }
        else{
            out.println(" Enter a single digit from 1 to 9 excluding 5");
            return false;
        }            
    }
   
/*==============================================================================    
 *
 * Timer stuff.
 *
 */    
    private void setTimer(){
        if(heartbeatTimer == null){
            heartbeatTimer = new Timer();
            timeOfLastMessage = System.currentTimeMillis();
            heartbeatTask = new ServerResponceTimer(this);
            heartbeatTimer.scheduleAtFixedRate(heartbeatTask, 30000, HEARTBEAT_INTERVAL);
        }
    }
    private void resetTimer(){
        timeOfLastMessage = System.currentTimeMillis();
    }
    void checkConnection(){
        Long time = System.currentTimeMillis();
        long timeLeft = CONNECTION_TIMEOUT - (time - timeOfLastMessage); //timeout - (elapsed time)
        
        //send heartbeat when less then 10 seconds is remaining before timeout.
        if ( timeLeft < 0){
            shutdown = true;
            sender.quitMsg();
            out.println("\n Connection to server timed-out, Shutting down.");
        } else if(timeLeft < 10000){
            heartbeatMessage = username + time.toString();
            sender.hbMsg(heartbeatMessage);
            //debug.println(" Sending HB message.");
        }
        //debug.printf("DEBUG: %s updating timer, %d milliseconds remaining\n",username,timeLeft);
    }
            
 
}
