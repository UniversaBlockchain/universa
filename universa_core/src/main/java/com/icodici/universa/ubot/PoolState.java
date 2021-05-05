/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.ubot;



public enum PoolState {
    /**
     * UBot creates new CloudProcessor with this state if it has received UBotCloudNotification, but CloudProcessor
     * with corresponding poolId not found. Then UBot calls method onNotifyInit for new CloudProcessor.
     */
    INIT,

    /**
     * At this state CloudProcessor should select ubots for new pool,
     * and periodically send to them udp notifications with invite to download requestContract.
     * Meanwhile, CloudProcessor is waiting for other ubots in pool to downloads requestContract.
     */
    SEND_STARTING_CONTRACT,

    /**
     * CloudProcessor is downloading requestContract from pool starter ubot.
     */
    DOWNLOAD_STARTING_CONTRACT,

    /**
     * CloudProcessor is executing cloud method.
     */
    START_EXEC,

    /**
     * CloudProcessor is finished.
     */
    FINISHED,

    /**
     * CloudProcessor is failed.
     */
    FAILED;


    public boolean isRunning() {
        return this != FINISHED && this != FAILED;
    }
}
