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

import java.io.PrintWriter;

/** 
 * Message sender for Text based Game Client
 * Handles the formatting and sending of messages.
 * @author Joel Cranston
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
