package com.icodici.universa.contract.jsapi;

/**
 * Instance of this class required as parameter in JSApi methods, that are public, but should be hidden from client js.
 * We don't give access to this class for js code, so it can't execute methods with JSApiAccessor parameter.
 * E.g.: {@link JSApiContract#extractContract()}
 */
public class JSApiAccessor {
}
