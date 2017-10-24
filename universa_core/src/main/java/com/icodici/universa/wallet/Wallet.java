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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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



    /**
     * Find wallets in the given path including all subfolders. Looking for files with .unic and .unc extensions.
     *
     * @param path
     *
     * @return
     */

    public static List<Wallet> findWallets(String path) {
        return determineWallets(findContracts(path));
    }



    /**
     * Find contracts in the given path including all subfolders. Looking for files with .unic and .unc extensions.
     *
     * @param path
     *
     * @return
     */

    private static List<Contract> findContracts(String path) {
        // TODO: Check if necessary to move function to Contract class.

        List<Contract> foundContracts = new ArrayList<>();
        List<File> foundContractFiles = new ArrayList<>();

        fillWithContractsFiles(foundContractFiles, path);

        Contract contract;
        for (File file : foundContractFiles) {
            try {
                contract = loadContract(file.getAbsolutePath());
                foundContracts.add(contract);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return foundContracts;
    }


    /**
     * Fill given List with contract files, found in given path recursively.
     *
     * Does not return new Lists but put found files into the given List for optimisation purposes.
     *
     * @param foundContractFiles
     * @param path
     */
    private static void fillWithContractsFiles(List<File> foundContractFiles, String path) {
        // TODO: Check if necessary to move function to Contract class.

        File pathFile = new File(path);

        if(pathFile.exists()) {
            ContractFilesFilter filter = new ContractFilesFilter();
            DirsFilter dirsFilter = new DirsFilter();

            if (pathFile.isDirectory()) {
                File[] foundFiles = pathFile.listFiles(filter);
                foundContractFiles.addAll(Arrays.asList(foundFiles));

                File[] foundDirs = pathFile.listFiles(dirsFilter);
                for (File file : foundDirs) {
                    fillWithContractsFiles(foundContractFiles, file.getPath());
                }
            } else {
                if (filter.accept(pathFile)) {
                    foundContractFiles.add(pathFile);
                }
            }
        }
    }

    private static Contract loadContract(String fileName) throws IOException {
        // TODO: resolve Contract bug: Contract cannot be initiated from sealed data until
        // Permissions beaing created or initialized or something like that.


        // TODO: same method exist in the com.icodici.universa.client.CLIMain.
        // Check if necessary to move function to Contract class.
        loadContractHook();

        Contract contract;

        Path path = Paths.get(fileName);
        byte[] data = Files.readAllBytes(path);

        contract = new Contract(data);

        return contract;
    }

    // This method is a hook, it resolve Contract bug: Contract cannot be initiated from sealed data until
    // Permissions beaing created or initialized or something like that.
    private static void loadContractHook() throws IOException {
        Contract.fromYamlFile("./src/test_contracts/simple_root_contract.yml");
    }

    static class ContractFilesFilter implements FileFilter {

        List<String> extensions = Arrays.asList("unic", "unc");

        ContractFilesFilter() {
        }

        public boolean accept(File pathname) {
            String extension = getExtension(pathname);
            for (String unc : extensions) {
                if(unc.equals(extension)) {
                    return true;
                }
            }
            return false;
        }

        private String getExtension(File pathname) {
            String filename = pathname.getPath();
            int i = filename.lastIndexOf('.');
            if ( i>0 && i<filename.length()-1 ) {
                return filename.substring(i+1).toLowerCase();
            }
            return "";
        }

    }

    static class DirsFilter implements FileFilter {

        DirsFilter() {
        }

        public boolean accept(File pathname) {
            return pathname.isDirectory();
        }

    }
}
