/*
 * message sender for Text based Game Client
 * Copyright (c) 2014, Joel Cranston. All rights reserved.
 */

package TextClient;

import java.io.PrintWriter;

/**
 * Handled the formatting and sending of messages.
 * @author joel
 */
public class MessageSender {
    static final String LOGIN = "L";
    static final String CONNECT = "C";
    static final String START = "S";    
    static final String MOVE = "M";
    static final String FIRE = "F";
    static final String SCAN = "P";
    static final String QUIT = "Q";
    static final String HEART = "H";
    static final String GAMELST = "G";
    private final PrintWriter out;
    
    public MessageSender(PrintWriter out){
        this.out = out;
    }   
    public void loginMsg(String s){
        out.println(LOGIN + s);
    }
    public void connectMsg(String s){
        out.println(CONNECT + s);
    }
    public void startMsg(Integer x, Integer y){
        out.println(START+ x.toString() + y.toString());
    }
    public void moveMsg(Integer d){
        out.println(MOVE + d.toString());
    }
    public void fireMsg(Integer x, Integer y){
        out.println(FIRE+ x.toString() + y.toString());
    }    
    public void scanMsg(Integer x, Integer y){
        out.println(SCAN + x.toString() + y.toString());
    }

    /**
     * Sends a user formatted message to the server.
     * @param ta is a properly formated message
     */
    public void genericMessage(String ta){
        out.println(ta);
    }
    public void gamelistMsg(){
        out.println(GAMELST);
    }         
    public void quitMsg(){
        out.println(QUIT);
    }
    public void hbMsg(String s){
        out.println(HEART + s);
    }
}
