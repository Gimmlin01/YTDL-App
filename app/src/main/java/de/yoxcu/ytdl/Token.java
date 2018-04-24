package de.yoxcu.ytdl;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;

/**
 * Created by yoxcu on 27.03.18.
 */


public class Token {
    private String token;
    private int duration;
    private long time;

    public Token(String token,int duration) {
        this.token=token;
        this.duration=duration;
        this.time=System.currentTimeMillis();
    }

    public Token() {
        this.time=System.currentTimeMillis();
    }

    public String getAuth() {
        return token+":";
    }

    public Boolean isAlive(){
        return (this.time + this.duration*1000 >=System.currentTimeMillis());
    }

    public String toJson(){
        try{
            Gson gson = new GsonBuilder().create();
            return gson.toJson(this);
        }catch (Exception e){
            e.printStackTrace();
            return "";
        }
    }

    public static Token fromJson(String json){
        try{
            Gson gson = new GsonBuilder().create();
            return gson.fromJson(json,Token.class);
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
}