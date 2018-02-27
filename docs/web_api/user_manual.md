Node Web API
==================================
Documentation
-----------

### Overview

Use this API for web based applications to work with the Universa. 

API presents interface for work with prepared and ready contracts as packed binary. Contract should be packed as Parcel or as TransactionPack for specific types of contract. Answers is returned as Binder structure. 

### Registering contracts

#### Register contract via parcel

Most common way to register contract is register it via Parcel. Parcel is pair of your contract and transaction units contract that will pay for processing. For register contract in the Universa via parcel use command 

    approveParcel
    
with param `packedItem` with packed Parcel binary. 

Command return Binder with key `result`. There are will be true if processing successfully started. Current processing state you can know via `getParcelProcessingState` command.


#### Register specific types of contracts without parcel

In some cases Universa allow you to register contracts without Parcel. Now you can do it for registering transaction units contract. So, you can register described contracts via command 

    approve
    
with param `packedItem` with packed TransactionPack binary. 

Command return Binder with key `itemResult`. There are will be enum ItemResult with current processing result and state of the contract. Use `getState` command to check contract result and state later.


### Check contracts states and its processing states

#### Check parcel processing state

After you launch parcel processing via `approveParcel` command, you can know its processing state. Do it using 

    getParcelProcessingState
    
with param `parcelId` with hashId of the Parcel.

Command return Binder with key `processingState`. There are will be enum Node.ParcelProcessingState. States `Node.ParcelProcessingState.FINISHED` and `Node.ParcelProcessingState.NOT_EXIST` means Parcel is processed and removed from the Node. Use `getState` to know your contract state from now.

#### Check contract state

You can check contract state via command 

    getState
    
with param `itemId` with hashId of the Contract you want to check.

Command return Binder with key `itemResult`. There are will be ItemResult type. ItemResult has field `state` with ItemState enum.

While processing and after contract has some states defined in the ItemState. 
This states means contract is being processing: 
* `PENDING`
* `PENDING_POSITIVE`
* `PENDING_NEGATIVE`

and this states means contract was processed:

* `APPROVED` – all is ok, contract registered.
* `DECLINED` – the contract can’t be accepted, for example, it has errors, wrong links, already processed.
* `REVOKED` – the contract was recently revoked. This state is not kept for long, the network has short memory of discarded items.
* `UNDEFINED` – the election failed for example due to severe network outage. You can try again soon.