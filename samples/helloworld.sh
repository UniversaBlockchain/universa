#!/bin/bash

f=/tmp/script.js
out="./files/contract-helloworld.unicon"

if [ ! -f $out ]; then

  printf "\ncreate javascript file with hello world code...\n"
  rm $f
  cat > $f << EndOfMessage
var jsApiEvents = new Object();
jsApiEvents.main = function() {
  print('hello world main()');
};
EndOfMessage

  printf "\ncreate new contract with this javascript...\n"
  java -jar ./files/uniclient.jar --create-contract-with-js $f -o $out -k ./files/key.priv

  printf "\nregister contract...\n"
  java -jar ./files/uniclient.jar --register $out -k ./files/key.priv -u ./files/Upackage.unicon

else

  printf "\ncontract $out already exists, registration skipped\n"

fi

printf "\nexecute js from contract...\n"
java -jar ./files/uniclient.jar --exec-js $out

printf "\ndone\n"
