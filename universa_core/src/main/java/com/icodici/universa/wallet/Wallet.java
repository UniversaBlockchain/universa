/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Maxim Pogorelov <pogorelovm23@gmail.com>, 10/20/17.
 *
 */


package com.icodici.universa.wallet;

import com.icodici.universa.Decimal;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.permissions.SplitJoinPermission;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.*;


public class Wallet {

    public static final int MAX_SELECTIONS_OF_CONTRACTS = 7;

    private List<Contract> contracts;


    public Wallet() {
        this.contracts = new ArrayList<>();
    }


    /**
     * Join this contract with other (split when the sum of contracts is greater) or
     * split the current one to have the input value in the one contract.
     *
     * @param fieldName
     * @param value     value to have in a one contract
     * @return
     */
    public synchronized Contract buildContractWithValue(String fieldName, @NonNull Decimal value) {
        if (value == null || Decimal.ZERO.equals(value) || this.contracts.size() == 0) return null;

        Contract result = null;

        //sort here because someone could add a new contract to the list at any time
        this.contracts.sort(Comparator.comparing(c -> getValue(c, fieldName)));

        List<Contract> selectedContracts = new ArrayList<>();

        int contractsSize = this.contracts.size();
        Decimal sum = Decimal.ZERO;

        for (int i = 0; i < contractsSize; i++) {
            Contract contract = this.contracts.get(i);

            Decimal cValue = getValue(contract, fieldName);
            sum = sum.add(cValue);

            if (selectedContracts.size() >= MAX_SELECTIONS_OF_CONTRACTS
                    && selectedContracts.get(i % MAX_SELECTIONS_OF_CONTRACTS) != null) {
                sum.subtract(getValue(selectedContracts.get(i % MAX_SELECTIONS_OF_CONTRACTS), fieldName));
                selectedContracts.set(i % MAX_SELECTIONS_OF_CONTRACTS, contract);
            } else
                selectedContracts.add(i % MAX_SELECTIONS_OF_CONTRACTS, contract);


            int compared = sum.compareTo(value);
            if (compared == 0) {
                result = joinAndRemoveFromContracts(selectedContracts);
                result.getStateData().set(fieldName, sum);
                break;
            } else if (compared == 1) {
                result = joinAndRemoveFromContracts(selectedContracts);
                result.getStateData().set(fieldName, sum);

                //split with change and add it back to the contracts
                Contract newContract = result.splitValue(fieldName, sum.subtract(value));

                this.contracts.add(newContract);
                break;
            }
        }

        return result;
    }

    private Contract joinAndRemoveFromContracts(List<Contract> selectedContracts) {
        Contract result = selectedContracts.get(0).copy();

        result.getRevokingItems().addAll(selectedContracts);
        this.contracts.removeAll(selectedContracts);

        return result;
    }

    private Decimal getValue(Contract c, String fieldName) {
        return new Decimal(c.getStateData().getStringOrThrow(fieldName));
    }


    public static List<Wallet> determineWallets(List<Contract> contracts) {
        Map<Object, Wallet> wallets = new HashMap<>();

        for (Contract contract : contracts) {
            if (contract.getPermissions() == null) continue;
            Collection<Permission> splitJoinCollection = contract.getPermissions().get("split_join");

            if (splitJoinCollection == null || splitJoinCollection.size() == 0) continue;

            Object split_join = contract.getPermissions().get("split_join").toArray()[0];

            if (!(split_join instanceof SplitJoinPermission)) continue;

            Object field_name = ((SplitJoinPermission) split_join).getParams().get("field_name");

            Wallet wallet = wallets.get(field_name);

            if (wallet == null) {
                wallet = new Wallet();
            }

            wallet.addContract(contract);
            wallets.put(field_name, wallet);
        }


        return new ArrayList<>(wallets.values());
    }

    public Wallet addContract(Contract contract) {
        this.contracts.add(contract);
        return this;
    }


    public List<Contract> getContracts() {
        return contracts;
    }

    public Wallet setContracts(List<Contract> contracts) {
        this.contracts = contracts;
        return this;
    }
}
