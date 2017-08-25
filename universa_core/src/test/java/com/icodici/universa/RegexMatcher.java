package com.icodici.universa;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import java.util.regex.Pattern;

public class RegexMatcher extends BaseMatcher {
    private final String regex;

    public RegexMatcher(String regex){
        this.regex = regex;
    }

    public boolean matches(Object o){
        Pattern pn = Pattern.compile(regex, Pattern.MULTILINE | Pattern.DOTALL);
        return pn.matcher((CharSequence)o).matches();
    }

    public void describeTo(Description description){
        description.appendText("matches regex /"+regex+"/");
    }

    public static RegexMatcher matches(String regex){
        return new RegexMatcher(regex);
    }
}
