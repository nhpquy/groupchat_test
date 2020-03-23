package com.smack.example.communicate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class GsonSingleton {
    private static Gson gson = new Gson();
    private static GsonBuilder builder = new GsonBuilder();

    public static Gson getInstance() {
        if (gson == null)
            gson = new Gson();
        return gson;
    }

    public static GsonBuilder builder() {
        return builder;
    }
}
