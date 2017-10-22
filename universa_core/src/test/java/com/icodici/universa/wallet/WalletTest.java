/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Maxim Pogorelov <pogorelovm23@gmail.com>, 10/20/17.
 *
 */


package com.icodici.universa.wallet;

import com.icodici.universa.Decimal;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractTestBase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;


public class WalletTest extends ContractTestBase {

    public static final String FIELD_NAME = "amount";

    @Test
    public void shouldDetermineWallets() throws Exception {
        List<Contract> listOfCoinsWithAmount = createListOfCoinsWithAmount(Arrays.asList(1, 2, 5, 8, 10 , 34, 45, 120));


        List<Wallet> wallets = Wallet.determineWallets(listOfCoinsWithAmount);

        assertEquals(1, wallets.size());
        assertEquals(8, wallets.get(0).getContracts().size());
    }

    @Test
    public void shouldTransferAmountFromCoupleContracts() throws Exception {
        Decimal valueToSend = new Decimal(70);

        List<Contract> listOfCoinsWithAmount = createListOfCoinsWithAmount(Arrays.asList(50, 45));


        List<Wallet> wallets = Wallet.determineWallets(listOfCoinsWithAmount);

        assertEquals(1, wallets.size());
        assertEquals(2, wallets.get(0).getContracts().size());


        //gonna send 70 but I have 2 contracts (50, 45)
        Wallet wallet = wallets.get(0);
        Contract contract = wallet.buildContractWithValue(FIELD_NAME, valueToSend);
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        assertEquals(1, wallet.getContracts().size());
        sealCheckTrace(contract, true);

        Contract contractToSend = new Contract(contract.seal());
        contractToSend.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(contractToSend, true);
        assertEquals(valueToSend.intValue(), contractToSend.getStateData().getIntOrThrow(FIELD_NAME));
    }

    @Test
    public void shouldTransferAndSplitRest() throws Exception {
        Decimal valueToSend = new Decimal(15);

        List<Contract> listOfCoinsWithAmount = createListOfCoinsWithAmount(Arrays.asList(1, 2, 3, 5, 8));

        List<Wallet> wallets = Wallet.determineWallets(listOfCoinsWithAmount);

        assertEquals(1, wallets.size());
        assertEquals(5, wallets.get(0).getContracts().size());


        Wallet wallet = wallets.get(0);
        Contract contract = wallet.buildContractWithValue(FIELD_NAME, valueToSend);
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        assertEquals(1, wallet.getContracts().size());
        sealCheckTrace(contract, true);

        Contract restContract = wallet.getContracts().get(0);
        assertEquals(4, restContract.getStateData().getIntOrThrow(FIELD_NAME));
        sealCheckTrace(restContract, true);

        Contract contractToSend = new Contract(contract.seal());
        contractToSend.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(contractToSend, true);
        assertEquals(valueToSend.intValue(), contractToSend.getStateData().getIntOrThrow(FIELD_NAME));
    }

    @Test
    public void shouldTransferTheSameValue() throws Exception {
        Decimal valueToSend = new Decimal(5);

        List<Contract> listOfCoinsWithAmount = createListOfCoinsWithAmount(Arrays.asList(5));

        List<Wallet> wallets = Wallet.determineWallets(listOfCoinsWithAmount);

        assertEquals(1, wallets.size());
        assertEquals(1, wallets.get(0).getContracts().size());


        Wallet wallet = wallets.get(0);
        Contract contract = wallet.buildContractWithValue(FIELD_NAME, valueToSend);
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        sealCheckTrace(contract, true);

        assertEquals(0, wallet.getContracts().size());

        Contract contractToSend = new Contract(contract.seal());
        contractToSend.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(contractToSend, true);
        assertEquals(valueToSend.intValue(), contractToSend.getStateData().getIntOrThrow(FIELD_NAME));
    }

    @Test
    public void shouldTransferSumOf7() throws Exception {
        Decimal valueToSend = new Decimal(280);

        List<Contract> listOfCoinsWithAmount = createListOfCoinsWithAmount(Arrays.asList(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60));

        List<Wallet> wallets = Wallet.determineWallets(listOfCoinsWithAmount);

        assertEquals(1, wallets.size());
        assertEquals(12, wallets.get(0).getContracts().size());


        Wallet wallet = wallets.get(0);
        Contract contract = wallet.buildContractWithValue(FIELD_NAME, valueToSend);
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        sealCheckTrace(contract, true);

        assertEquals(6, wallet.getContracts().size());

        Contract contractToSend = new Contract(contract.seal());
        contractToSend.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(contractToSend, true);
        assertEquals(valueToSend.intValue(), contractToSend.getStateData().getIntOrThrow(FIELD_NAME));
    }

    private List<Contract> createListOfCoinsWithAmount(List<Integer> values) throws Exception {
        List<Contract> contracts = new ArrayList<>();


        for (Integer value : values) {
            Contract contract = createCoin();
            contract.getStateData().set(FIELD_NAME, new Decimal(value));
            contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);

            sealCheckTrace(contract, true);

            contracts.add(contract);
        }

        return contracts;
    }

}
