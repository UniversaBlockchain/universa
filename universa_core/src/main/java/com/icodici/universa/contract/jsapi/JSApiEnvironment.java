package com.icodici.universa.contract.jsapi;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import net.sergeych.tools.Binder;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JSApiEnvironment {

    private Contract currentContract;
    private JSApiScriptParameters scriptParameters;
    private JSApi jsApi;
    private ScriptEngine scriptEngine;
    private Object result;
    private String handlerMethodName;

    public Object getResult() {
        return result;
    }

    public Contract getCurrentContract() {
        return currentContract;
    }

    public String getHandlerMethodName() {
        return handlerMethodName;
    }

    public void setHandlerMethodName(String newValue) {
        handlerMethodName = newValue;
    }

    public static JSApiEnvironment execJS(Binder definitionScripts, Binder stateScripts, JSApiExecOptions execOptions,
                                byte[] jsFileContent, Contract currentContract, String... params)
            throws Exception {
        HashId jsFileHashId = HashId.of(jsFileContent);
        Binder scriptBinder = JSApiHelpers.findScriptBinder(definitionScripts, jsFileHashId);
        if (scriptBinder == null)
            scriptBinder = JSApiHelpers.findScriptBinder(stateScripts, jsFileHashId);
        if (scriptBinder != null) {
            return execJSImpl(execOptions, jsFileContent, currentContract, scriptBinder, params);
        } else {
            throw new IllegalArgumentException("error: cant exec javascript, script hash not found in contract.");
        }
    }

    public static JSApiEnvironment execJSByScriptHash(Binder definitionScripts, Binder stateScripts, JSApiExecOptions execOptions,
                                          HashId jsFileHashId, Contract currentContract, String... params)
            throws Exception {
        Binder scriptBinder = JSApiHelpers.findScriptBinder(definitionScripts, jsFileHashId);
        if (scriptBinder == null)
            scriptBinder = JSApiHelpers.findScriptBinder(stateScripts, jsFileHashId);
        if (scriptBinder != null) {
            byte[] jsFileContent = scriptBinder.getBinaryOrThrow("file_content");
            return execJSImpl(execOptions, jsFileContent, currentContract, scriptBinder, params);
        } else {
            throw new IllegalArgumentException("error: cant exec javascript, script hash not found in contract.");
        }
    }

    public static JSApiEnvironment execJSByName(Binder definitionScripts, Binder stateScripts, JSApiExecOptions execOptions,
                                                      String jsFileName, Contract currentContract, String... params)
            throws Exception {
        Binder scriptBinder = JSApiHelpers.findScriptBinderByFileName(definitionScripts, jsFileName);
        if (scriptBinder == null)
            scriptBinder = JSApiHelpers.findScriptBinderByFileName(stateScripts, jsFileName);
        if (scriptBinder != null) {
            byte[] jsFileContent = scriptBinder.getBinaryOrThrow("file_content");
            return execJSImpl(execOptions, jsFileContent, currentContract, scriptBinder, params);
        } else {
            throw new IllegalArgumentException("error: cant exec javascript, script file_name not found in contract.");
        }
    }

    private static JSApiEnvironment execJSImpl(JSApiExecOptions execOptions, byte[] jsFileContent, Contract currentContract,
                                               Binder scriptBinder, String... params) throws Exception {
        JSApiEnvironment environment = new JSApiEnvironment();
        environment.currentContract = currentContract;
        environment.scriptParameters = JSApiScriptParameters.fromBinder(scriptBinder);
        environment.scriptEngine = new NashornScriptEngineFactory().getScriptEngine(s -> false);
        environment.jsApi = new JSApi(currentContract, execOptions, environment.scriptParameters);
        environment.scriptEngine.put("jsApi", environment.jsApi);
        String[] stringParams = new String[params.length];
        for (int i = 0; i < params.length; ++i)
            stringParams[i] = params[i].toString();
        environment.scriptEngine.put("jsApiParams", stringParams);
        String jsString = JSApiHelpers.unpackJSString(scriptBinder, jsFileContent);
        List<Exception> exceptionsFromEval = new ArrayList<>();
        Thread evalThread = new Thread(()-> {
            try {
                environment.scriptEngine.eval(jsString);
            } catch (Exception e) {
                exceptionsFromEval.add(e);
            }
        });
        evalThread.start();
        if (environment.scriptParameters.timeLimitMillis == 0) {
            evalThread.join();
        } else {
            try {
                evalThread.join(environment.scriptParameters.timeLimitMillis);
                if (evalThread.isAlive())
                    throw new InterruptedException("error: client javascript time limit is up (limit=" + environment.scriptParameters.timeLimitMillis + "ms)");
            } catch (InterruptedException e) {
                throw new InterruptedException("error: client javascript was interrupted (limit=" + environment.scriptParameters.timeLimitMillis + "ms)");
            }
        }
        if (exceptionsFromEval.size() > 0) {
            Exception e = exceptionsFromEval.get(0);
            throw e;
        }
        environment.result = environment.scriptEngine.get("result");
        return environment;
    }

    public void callEvent(String eventName, Boolean silently, Object... params) throws InterruptedException {
        Thread evalThread = new Thread(()-> {
            try {
                Object jsApiEvents = scriptEngine.get("jsApiEvents");
                if (jsApiEvents == null) {
                    if (!silently)
                        System.err.println("JSApiHttpServer error: jsApiEvents object not found in client javascript");
                } else {
                    Invocable invocable = (Invocable) scriptEngine;
                    invocable.invokeMethod(
                            jsApiEvents,
                            eventName,
                            params
                    );
                }
            } catch (NoSuchMethodException e) {
                if (!silently)
                    System.err.println("JSApiEnvironment error(NoSuchMethodException) -  " + eventName + ": " + e);
            } catch (ScriptException e) {
                System.err.println("JSApiEnvironment error: " + e);
                e.printStackTrace();
            }
        });
        evalThread.start();
        if (scriptParameters.timeLimitMillis == 0) {
            evalThread.join();
        } else {
            try {
                evalThread.join(scriptParameters.timeLimitMillis);
                if (evalThread.isAlive())
                    throw new InterruptedException("error: client javascript (eventName:"+eventName+") time limit is up (limit=" + scriptParameters.timeLimitMillis + "ms)");
            } catch (InterruptedException e) {
                throw new InterruptedException("error: client javascript (eventName:"+eventName+") was interrupted (limit=" + scriptParameters.timeLimitMillis + "ms)");
            }
        }
    }

}
