# About

Here you can find sample bash scripts, that creates, registers and executes smart contracts with attached javascript.

To run this samples you should provide 3 files: uniclient.jar, your private key and your U package.
Create new folder samples/files and put it here like:
./files
    uniclient.jar
    key.priv
    Upackage.unicon



# helloworld.sh

Simple script that handle main js-api entry point and just print something.



# start-http-server-from-slot1.sh

We have saved (in SLOT1) example contract with attached http-javascript server inside.
This sample starts the server directly from SLOT1 by its slotId and originId.
When server started, try to open in your browser:
http://localhost:8880/contract1/getRevision
http://localhost:8880/contract1/getTime
http://localhost:8880/contract1/getIssuer



# http-server.sh

This sample creates contract with attached http-javascript server code. Registers it and run it.
Endpoints are the same as in previous sample.



# http-client.sh

Creates and registers contract that make test request to endpoints from above server contracts, through js-api.
Just executes its attached javascript, if contract already created.
