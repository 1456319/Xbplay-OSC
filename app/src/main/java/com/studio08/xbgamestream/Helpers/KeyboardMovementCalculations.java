package com.studio08.xbgamestream.Helpers;

import android.util.Log;

import com.studio08.xbgamestream.Helpers.Helper;

import java.util.ArrayList;
import java.util.List;

public class KeyboardMovementCalculations {
    private Character start;
    private Character destination;
    private String keyboardType;

    // hulu, netflix, prime, hbo
    final char [][] netflixKeyboard = new char[][] {
            {'a', 'b', 'c', 'd', 'e', 'f'},
            {'g', 'h', 'i', 'j', 'k', 'l'},
            {'m', 'n', 'o', 'p', 'q', 'r'},
            {'s', 't', 'u', 'v', 'w', 'x'},
            {'y', 'z', '1', '2', '3', '4'},
            {'5', '6', '7', '8', '9', '0'}
    };

    final char [][] disneyKeyboard = new char[][] {
            { 'a', 'b', 'c', 'd', 'e', 'f', 'g' },
            { 'h', 'i', 'j', 'k', 'l', 'm', 'n' },
            { 'o', 'p', 'q', 'r', 's', 't', 'u' },
            { 'v', 'w', 'x', 'y', 'z' },
            { '1', '2', '3', '4', '5', '6', '7',},
            { '8', '9', '0'}
    };

    final char [][] systemKeyboard = new char[][] { // keeping all normal chars but frontend will sanatize these
            { '1', '2', '3', '4', '5', '6', '7', '8', '9', '0' },
            { 'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p' },
            { 'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', '"' },
            { 'z', 'x', 'c', 'v', 'b', 'n', 'm', ';', ':', '!' },
    };

    final char [][] peacock = new char[][] { // putting fake chars ~ in there because navigation is consistent
            { 'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p' },
            { 'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', '~' },
            { 'z', 'x', 'c', 'v', 'b', 'n', 'm', '.', '~', '~' },
    };

    final char [][] vudu = new char[][] {
            {'a', 'b', 'c', 'd', 'e', 'f'},
            {'g', 'h', 'i', 'j', 'k', 'l'},
            {'m', 'n', 'o', 'p', 'q', 'r'},
            {'s', 't', 'u', 'v', 'w', 'x'},
            {'y', 'z', '0', '1', '2', '3'},
            {'4', '5', '6', '7', '8', '9'},
    };

    final char [][] youTube = new char[][] {
            { 'a', 'b', 'c', 'd', 'e', 'f', 'g' },
            { 'h', 'i', 'j', 'k', 'l', 'm', 'n' },
            { 'o', 'p', 'q', 'r', 's', 't', 'u' },
            { 'v', 'w', 'x', 'y', 'z', '-', '\'' },
    };

    public KeyboardMovementCalculations(String start, String destination, String keyboardType){
       this.start = start.charAt(0);
       this.destination = destination.charAt(0);
       this.keyboardType = keyboardType;
    }

    public byte[][] convertPositionsToByteArray(){
        this.start = Character.toLowerCase(this.start);
        this.destination = Character.toLowerCase(this.destination);
        List<byte []> finalMovementSequence = new ArrayList<>();

        char [][] keyboard = null;
        boolean primeMovement = false; //account for non rectangle keyboards
        if(keyboardType.equals("netflix")){
            keyboard = netflixKeyboard;
        } else if (keyboardType.equals("disney")){
            keyboard = disneyKeyboard;
            primeMovement = true;
        } else if (keyboardType.equals("system_keyboard")){
            keyboard = systemKeyboard;
        } else if (keyboardType.equals("peacock")){
            keyboard = peacock;
        }  else if (keyboardType.equals("vudu")){
            keyboard = vudu;
            primeMovement = true;
        }  else if (keyboardType.equals("youtube")){
            keyboard = youTube;
        } else {
            return null;
        }

        Integer movementVertical = calcMovement(keyboard, true);
        Integer movementHorizontal = calcMovement(keyboard, false);

        if(movementHorizontal == null || movementVertical == null){
            return null;
        }
        List<byte []> primeMovementList = new ArrayList<>();

        // handle keyboard with non rectangle shapes if moving vertically
        if(primeMovement && (Math.abs(movementVertical) > 0)) {
            // create list of movements to get to column 0
            int startLocationHorizontal = this.findLocation(keyboard, this.start, false);
            for (int i = 0; i < startLocationHorizontal; i++) {
                primeMovementList.add(Helper.convertStringButtonToByteArray("dpadLeft"));
            }
            // update start location to reflect above movements
            int startLocationVertical = this.findLocation(keyboard, this.start, true);
            this.start = keyboard[startLocationVertical][0]; // reset start position to first char at same row
            movementHorizontal = calcMovement(keyboard, false); // recalc vertical movement
        }

        // first add primer movement (if any)
        finalMovementSequence.addAll(primeMovementList);

        // next move vertically first (important for non rectangle keyboards)
        List<byte []> verticalButtonSequence = convertLocationIntToSequence(movementVertical, true);
        finalMovementSequence.addAll(verticalButtonSequence);


        // move horizontally
        List<byte []> horizontalButtonSequence = convertLocationIntToSequence(movementHorizontal, false);
        finalMovementSequence.addAll(horizontalButtonSequence);

        // add final select sequence
        finalMovementSequence.add(Helper.convertStringButtonToByteArray("a")); // add select at end

        // create output byte array
        byte [][] result = new byte[finalMovementSequence.size()][];
        for(int i = 0; i < finalMovementSequence.size(); i++){
            result[i] = finalMovementSequence.get(i);
        }
        Log.e("HRE", "Seq Array: " + result);
        return result;
    }

    private List<byte []> convertLocationIntToSequence(Integer movement, boolean isVertical){
        String direction;
        List<byte []> list = new ArrayList<>();

        if(isVertical){
            if(movement < 0){
                direction = "dpadDown";
            } else {
                direction = "dpadUp";
            }
        } else {
            if(movement < 0){
                direction = "dpadRight";
            } else {
                direction = "dpadLeft";
            }
        }

        Log.e("JERE", "Move Direction: " + direction + " Move Quantity: " + Math.abs(movement) + " Dest: " + this.destination + " Start: " + this.start);
        for(int i = 0; i < Math.abs(movement); i++){
            list.add(Helper.convertStringButtonToByteArray(direction));
        }

        return list;
    }

    private Integer calcMovement(char [][] keyboardLayout, boolean vertical) {
        int startLocation = findLocation(keyboardLayout, this.start, vertical);
        int destLocation = findLocation(keyboardLayout, this.destination, vertical);

        if(startLocation != -1 && destLocation != -1){
            return startLocation - destLocation;
        } else {
            return null;
        }
    }

    private int findLocation(char[][] keyboardLayout, char dest, boolean vertical){
        for (int i = 0; i < keyboardLayout.length; i++){
            for (int j = 0; j < keyboardLayout[i].length; j++){
                char currentChar = keyboardLayout[i][j];
                if(currentChar == dest){
                    if(vertical) {
                        return i;
                    } else {
                        return j;
                    }
                }
            }
        }
        return -1;
    }
}
