package com.smack.example;

import org.apache.commons.lang3.RandomStringUtils;

public class RandomUtils {
    public static String randomString(int length) {
        return RandomStringUtils.randomAlphabetic(length);
    }

    public static String randomAlphanumericString(int length) {
        return RandomStringUtils.randomAlphanumeric(length);
    }

    public static String randomNumber(int length) {
        return RandomStringUtils.randomNumeric(length);
    }

    public static String randomAlphanumericLowerCaseString(int length) {
        return RandomStringUtils.randomAlphanumeric(length).toLowerCase();
    }

    public static String randomAlphanumericUpperCaseString(int length) {
        return RandomStringUtils.randomAlphanumeric(length).toUpperCase();
    }

    public static void main(String[] args) {
        System.out.println(randomString(16));
        System.out.println(randomString(16));

        String data = "id=527299717&first_name=Thong&last_name=Nguyen&username=ndThong&photo_url=https%3A%2F%2Ft.me%2Fi%2Fuserpic%2F320%2FRcLojTDsS5vFOIJGcSileJ4xSqGZR5rRuQe881Fig14.jpg&auth_date=1578051482&hash=006522aa6da8d5a4a103ed553740a08f10a67c3601053ce80860754455dcb751";
    }
}
