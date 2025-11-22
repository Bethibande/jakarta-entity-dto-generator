package com.bethibande.process;

public class StringUtil {

    public static String firstCharacterUpperCase(final String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public static String firstCharacterLowerCase(final String input) {
        return input.substring(0, 1).toLowerCase() + input.substring(1);
    }

}
