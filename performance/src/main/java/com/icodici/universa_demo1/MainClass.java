package com.icodici.universa_demo1;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractsService;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.permissions.SplitJoinPermission;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.network.Client;
import com.icodici.universa.node2.network.ClientError;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainClass {

    private static final String BASE_DEVKEYS_PATH = System.getProperty("user.home")+"/.universa/dev_keys/";


    public static final String ACC_TYPE_FIELD = "type";
    public static final String ACC_TYPE_PATH = "state.data." + ACC_TYPE_FIELD;
    public static final String ACC_TYPE_PERSONAL = "personal";
    public static final String ACC_TYPE_BANK = "bank";

    public static final String ACC_CURRENCY_FIELD = "currency";
    public static final String ACC_CURRENCY_PATH = "state.data." + ACC_CURRENCY_FIELD;
    public static final String ACC_CURRENCY_RUR = "RUR";
    public static final String ACC_CURRENCY_EUR = "EUR";

    public static final String ACC_COMMISSION_PERCENT_FIELD = "commission_percent";
    public static final String ACC_COMMISSION_ACCOUNT_FIELD = "commission_account";

    public static final String ACC_COMMISSION_PERCENT_PATH = "state.data." + ACC_COMMISSION_PERCENT_FIELD;
    public static final String ACC_COMMISSION_ACCOUNT_PATH = "state.data." + ACC_COMMISSION_ACCOUNT_FIELD;


    public static final String TOKEN_VALUE_FIELD = "amount";
    public static final String TOKEN_ACCOUNT_FIELD = "account";

    public static final String TOKEN_ACCOUNT_PATH = "state.data." + TOKEN_ACCOUNT_FIELD;
    public static final String TOKEN_VALUE_PATH = "state.data." + TOKEN_VALUE_FIELD;
    private static String totalAmount = "100000";



    public static Contract[] prepareTransfer(Contract token, BigDecimal amount, Contract fromAccount, Contract toAccount, Contract commissionAccount, Set<PrivateKey> keysToSignTransferWith, BigDecimal commissionAmount) throws ClientError {
        token = token.createRevision(keysToSignTransferWith);
        int partsCount = commissionAmount != null && commissionAmount.compareTo(new BigDecimal("0")) > 0 ? 2 : 1;
        Contract[] parts = token.split(partsCount);
        Contract transfer = parts[0];
        Contract commission = partsCount > 1 ? parts[1] : null;

        transfer.getStateData().set(TOKEN_VALUE_FIELD, amount);
        transfer.getKeysToSignWith().clear();
        BigDecimal rest = new BigDecimal(token.getStateData().getString(TOKEN_VALUE_FIELD))
                .subtract(new BigDecimal(transfer.getStateData().getString(TOKEN_VALUE_FIELD)));

        if (commission != null) {
            commission.getStateData().set(TOKEN_VALUE_FIELD, commissionAmount);
            commission.getKeysToSignWith().clear();
            rest = rest.subtract(commissionAmount);
        }

        token.getStateData().set(TOKEN_VALUE_FIELD, rest);
        token.seal();
        token.getTransactionPack().addReferencedItem(fromAccount);

        transfer = transfer.createRevision(keysToSignTransferWith);
        transfer.getStateData().set(TOKEN_ACCOUNT_FIELD, toAccount.getId().toBase64String());
        transfer.getKeysToSignWith().clear();
        transfer.seal();

        Contract batch;

        if (commission != null) {
            commission = commission.createRevision(keysToSignTransferWith);
            commission.getStateData().set(TOKEN_ACCOUNT_FIELD, fromAccount.get(ACC_COMMISSION_ACCOUNT_PATH));
            commission.getTransactionalData().set("transfer_id", transfer.getId().toBase64String());
            commission.getKeysToSignWith().clear();

            batch = ContractsService.createBatch(keysToSignTransferWith, transfer, commission, token);
        } else {
            batch = ContractsService.createBatch(keysToSignTransferWith, transfer, token);
        }


        batch.getTransactionPack().addReferencedItem(fromAccount);
        batch.getTransactionPack().addReferencedItem(toAccount);
        batch.getTransactionPack().addReferencedItem(commissionAccount);


        return new Contract[]{batch, token, transfer, commission};
    }

    public static void main(String... args) throws Exception {
        String network = "test";
        if(args.length > 0) {
            network = args[0];
        }

        PrivateKey clientKey = getDevKeyForNetwork(network);
        if(clientKey == null) {
            System.out.println("Dev key not found for network '" + network +"'. Make sure directory '"
                    + BASE_DEVKEYS_PATH+network + "' exists and contains universa private key file");
            return;
        }
        Client client = checkDevKeyForNetwork(clientKey,network);
        if(client == null) {
            System.out.println("Dev key check is failed. Make sure network is accessible and one of the addresses is in its whilelist:\n"+
                    clientKey.getPublicKey().getLongAddress()+"\n"+clientKey.getPublicKey().getShortAddress()+"\n");
        }


        PrivateKey person1 = new PrivateKey(2048);
        PrivateKey person2 = new PrivateKey(2048);
        PrivateKey bank = new PrivateKey(2048);


        Contract commissionAccRur = new Contract(bank);
        commissionAccRur.getStateData().put(ACC_TYPE_FIELD, ACC_TYPE_BANK);
        commissionAccRur.getStateData().put(ACC_CURRENCY_FIELD, ACC_CURRENCY_RUR);
        commissionAccRur.getStateData().put(ACC_COMMISSION_PERCENT_FIELD, "0.0");
        commissionAccRur.getStateData().put(ACC_COMMISSION_ACCOUNT_FIELD, null);
        commissionAccRur.seal();

        registerContract(commissionAccRur,client,"Registering account for commissions (RUR): " + commissionAccRur.getId());

        Contract commissionAccEur = new Contract(bank);
        commissionAccEur.getStateData().put(ACC_TYPE_FIELD, ACC_TYPE_BANK);
        commissionAccEur.getStateData().put(ACC_CURRENCY_FIELD, ACC_CURRENCY_EUR);
        commissionAccEur.getStateData().put(ACC_COMMISSION_PERCENT_FIELD, "0.0");
        commissionAccEur.getStateData().put(ACC_COMMISSION_ACCOUNT_FIELD, null);
        commissionAccEur.seal();

        registerContract(commissionAccEur,client,"Registering account for commissions (EUR): " + commissionAccEur.getId());

        System.out.println("\n====================\n");
        System.out.println("Person #1 address:" + person1.getPublicKey().getShortAddress());
        System.out.println("Person #2 address:" + person2.getPublicKey().getShortAddress());

        Contract account1rur = new Contract(person1);
        account1rur.getStateData().put(ACC_TYPE_FIELD, ACC_TYPE_PERSONAL);
        account1rur.getStateData().put(ACC_CURRENCY_FIELD, ACC_CURRENCY_RUR);
        account1rur.getStateData().put(ACC_COMMISSION_PERCENT_FIELD, "0.03");
        account1rur.getStateData().put(ACC_COMMISSION_ACCOUNT_FIELD, commissionAccRur.getId().toBase64String());
        account1rur.seal();

        registerContract(account1rur,client,"Registering RUR account for person #1: " + account1rur.getId()+
                "\nAccount commission is " + account1rur.get(ACC_COMMISSION_PERCENT_PATH));


        Contract account2rur = new Contract(person2);
        account2rur.getStateData().put(ACC_TYPE_FIELD, ACC_TYPE_PERSONAL);
        account2rur.getStateData().put(ACC_CURRENCY_FIELD, ACC_CURRENCY_RUR);
        account2rur.getStateData().put(ACC_COMMISSION_PERCENT_FIELD, "0.0");
        account2rur.getStateData().put(ACC_COMMISSION_ACCOUNT_FIELD, commissionAccRur.getId().toBase64String());
        account2rur.seal();

        registerContract(account2rur,client,"Registering RUR account for person #2: " + account2rur.getId()+
                "\nAccount commission is " + account2rur.get(ACC_COMMISSION_PERCENT_PATH));


        Contract account2eur = new Contract(person2);
        account2eur.getStateData().put(ACC_TYPE_FIELD, ACC_TYPE_PERSONAL);
        account2eur.getStateData().put(ACC_CURRENCY_FIELD, ACC_CURRENCY_EUR);
        account2eur.getStateData().put(ACC_COMMISSION_PERCENT_FIELD, "0.05");
        account2eur.getStateData().put(ACC_COMMISSION_ACCOUNT_FIELD, commissionAccEur.getId().toBase64String());
        account2eur.seal();

        registerContract(account2eur,client,"Registering EUR account for person #2: " + account2eur.getId() +
                "\nAccount commission is " + account2eur.get(ACC_COMMISSION_PERCENT_PATH));


        // TOKEN CREATION
        Contract rurTokenAcc1 = new Contract(bank);
        SimpleRole tokenOwner = new SimpleRole("owner");
        tokenOwner.addRequiredReference("canplayaccowner", Role.RequiredMode.ALL_OF);
        rurTokenAcc1.registerRole(tokenOwner);
        rurTokenAcc1.getStateData().put("account", account1rur.getId().toBase64String());
        rurTokenAcc1.getStateData().put(TOKEN_VALUE_FIELD, totalAmount);

        RoleLink rl = new RoleLink("@owner", "owner");
        rurTokenAcc1.registerRole(rl);
        SplitJoinPermission sjp =
                new SplitJoinPermission(rl, Binder.of("field_name", TOKEN_VALUE_FIELD,
                        "join_match_fields", Do.listOf("state.origin", TOKEN_ACCOUNT_PATH)));
        rurTokenAcc1.addPermission(sjp);


        ModifyDataPermission mdp =
                new ModifyDataPermission(rl, Binder.of("fields", Binder.of(TOKEN_ACCOUNT_FIELD, null)));
        rurTokenAcc1.addPermission(mdp);


        Reference canplayaccowner = new Reference(rurTokenAcc1);
        canplayaccowner.name = "canplayaccowner";
        List<String> conditions = new ArrayList<>();
        conditions.add("ref.id==this." + TOKEN_ACCOUNT_PATH);
        conditions.add("this can_play ref.owner");
        canplayaccowner.setConditions(Binder.of("all_of", conditions));
        rurTokenAcc1.addReference(canplayaccowner);


        Reference refAccount = new Reference(rurTokenAcc1);
        refAccount.name = "refAccount";
        refAccount.setConditions(Binder.of("all_of", Do.listOf("this." + TOKEN_ACCOUNT_PATH + " == ref.id")));
        rurTokenAcc1.addReference(refAccount);

        Reference refParent = new Reference(rurTokenAcc1);
        refParent.name = "refParent";
        refParent.setConditions(Binder.of("any_of", Do.listOf(
                "this.state.parent == ref.id",
                "this.state.parent undefined"
        )));
        rurTokenAcc1.addReference(refParent);

        Reference refParentAccount = new Reference(rurTokenAcc1);
        refParentAccount.name = "refParentAccount";
        refParentAccount.setConditions(Binder.of("any_of", Do.listOf(
                "refParent." + TOKEN_ACCOUNT_PATH + " == ref.id",
                "this.state.parent undefined"
        )));
        rurTokenAcc1.addReference(refParentAccount);


        Reference transferCheck = new Reference(rurTokenAcc1);
        transferCheck.name = "transferCheck";
        transferCheck.setConditions(Binder.of("any_of", Do.listOf(
                //root contract
                "this.state.parent undefined",

                //OR there was no transfer
                "refParent." + TOKEN_ACCOUNT_PATH + " == this." + TOKEN_ACCOUNT_PATH,

                //OR transfer is correct
                Binder.of("all_of", Do.listOf(

                        //transfer to account with same currency
                        "refAccount." + ACC_CURRENCY_PATH + " == refParentAccount." + ACC_CURRENCY_PATH,

                        //AND one of the cases
                        Binder.of("any_of", Do.listOf(
                                // contract is a commission itself
                                Binder.of("all_of", Do.listOf(
                                        "this." + TOKEN_ACCOUNT_PATH + "==refParentAccount." + ACC_COMMISSION_ACCOUNT_PATH
                                )),

                                // OR commission is set to zero
                                Binder.of("all_of", Do.listOf(
                                        "refParentAccount." + ACC_COMMISSION_PERCENT_PATH + "::number==0.0"
                                )),

                                // OR commission exists in pack
                                Binder.of("all_of", Do.listOf(
                                        "ref." + TOKEN_VALUE_PATH + "::number == this." + TOKEN_VALUE_PATH + "::number * refParentAccount." + ACC_COMMISSION_PERCENT_PATH + "::number",
                                        "ref." + TOKEN_ACCOUNT_PATH + " == refParentAccount." + ACC_COMMISSION_ACCOUNT_PATH,
                                        "ref.transactional.data.transfer_id == this.id"

                                ))
                        ))
                ))
        )));
        rurTokenAcc1.addReference(transferCheck);
        rurTokenAcc1.seal();
        rurTokenAcc1.getTransactionPack().addReferencedItem(account1rur);

        registerContract(rurTokenAcc1,client,"Issuing "+totalAmount+" RUR for account #1: " + rurTokenAcc1.getId());

        System.out.println("\n\n============ CORRECT OPERATIONS ==================\n\n");

        BigDecimal transfer1 = new BigDecimal(totalAmount).divide(new BigDecimal("4"));
        BigDecimal comission = transfer1.multiply(new BigDecimal(account1rur.get(ACC_COMMISSION_PERCENT_PATH).toString()));
        Contract[] ptransfer = prepareTransfer(rurTokenAcc1, transfer1, account1rur, account2rur, commissionAccRur, new HashSet<>(Do.listOf(person1)), comission);
        registerContract(ptransfer[0],client,"Sending " + transfer1 + " RUR from acc #1 (RUR) to acc #2 (RUR) with commission " + comission + " RUR");
        rurTokenAcc1 = ptransfer[1];
        Contract rurTokenAcc2 = ptransfer[2];

        BigDecimal transfer2 = new BigDecimal(rurTokenAcc2.get(TOKEN_VALUE_PATH).toString()).divide(new BigDecimal("4"));

        ptransfer = prepareTransfer(rurTokenAcc2, transfer2, account2rur, account1rur, commissionAccRur, new HashSet<>(Do.listOf(person2)), null);
        registerContract(ptransfer[0],client,"Sending " + transfer2 + " RUR from acc #2 (RUR) to acc #1 (RUR) without commission");

        rurTokenAcc2 = ptransfer[1];
        Contract rurTokenAcc1_2 = ptransfer[2];


        rurTokenAcc1 = rurTokenAcc1.createRevision(person1);
        rurTokenAcc1.addRevokingItems(rurTokenAcc1_2);
        rurTokenAcc1_2 = null;
        rurTokenAcc1.getStateData().set(TOKEN_VALUE_FIELD, new BigDecimal(rurTokenAcc1.get(TOKEN_VALUE_PATH).toString()).add(transfer2));
        rurTokenAcc1.seal();
        rurTokenAcc1.getTransactionPack().addReferencedItem(account1rur);
        registerContract(rurTokenAcc1,client,"Joining acc1 tokens");


        System.out.println("\n\n============ INCORRECT OPERATIONS ==================\n\n");

        Contract joinDiffAccs = rurTokenAcc1.createRevision(person1,person2);
        joinDiffAccs.addRevokingItems(rurTokenAcc2);
        joinDiffAccs.getStateData().set(TOKEN_VALUE_FIELD, new BigDecimal(rurTokenAcc1.get(TOKEN_VALUE_PATH).toString()).add(new BigDecimal(rurTokenAcc2.get(TOKEN_VALUE_PATH).toString())));
        joinDiffAccs.seal();
        joinDiffAccs.getTransactionPack().addReferencedItem(account1rur);
        joinDiffAccs.getTransactionPack().addReferencedItem(account2rur);
        registerContract(joinDiffAccs,client,"Joining acc #1  and acc #2 tokens - signed by both persons (TOKENS AREN'T JOINABLE ACROSS DIFFERENT ACCOUNTS)",ItemState.DECLINED);


        BigDecimal transfer3 = new BigDecimal(rurTokenAcc2.get(TOKEN_VALUE_PATH).toString()).divide(new BigDecimal("4"));
        ptransfer = prepareTransfer(rurTokenAcc2, transfer3, account2rur, account2eur, commissionAccRur, new HashSet<>(Do.listOf(person2)), null);
        registerContract(ptransfer[0],client,"Sending " + transfer3 + " RUR from acc #2 (RUR) to acc #2 (EUR) without commission (RUR -> EUR account transfer)",ItemState.DECLINED);


        BigDecimal transfer4 = new BigDecimal(rurTokenAcc1.get(TOKEN_VALUE_PATH).toString()).divide(new BigDecimal("4"));
        ptransfer = prepareTransfer(rurTokenAcc1, transfer4, account1rur, account2rur, commissionAccRur, new HashSet<>(Do.listOf(person1)), null);
        registerContract(ptransfer[0],client,"Sending " + transfer4 + " RUR from acc #1 (RUR) to acc #2 (RUR) without commission (COMMISSION REQUIRED)",ItemState.DECLINED);

        BigDecimal transfer5 = new BigDecimal(rurTokenAcc1.get(TOKEN_VALUE_PATH).toString()).divide(new BigDecimal("4"));
        ptransfer = prepareTransfer(rurTokenAcc1, transfer5, account1rur, account2rur, commissionAccRur, new HashSet<>(Do.listOf(person1)), new BigDecimal("0.0001"));
        registerContract(ptransfer[0],client,"Sending " + transfer5 + " RUR from acc #1 (RUR) to acc #2 (RUR) with insufficient commission (COMMISSION INSUFFICIENT)",ItemState.DECLINED);

    }
    private static void registerContract(Contract commissionAccRur, Client client, String message) throws ClientError {
        registerContract(commissionAccRur,client,message,ItemState.APPROVED);
    }

    private static void registerContract(Contract commissionAccRur, Client client, String message, ItemState stateExpected) throws ClientError {
        System.out.println(message);
        ItemResult ir = client.register(commissionAccRur.getPackedTransaction(), 8000);
        System.out.println(ir+"\n");
        if(ir.state != stateExpected) {
            System.exit(1);
        }
    }

    private static PrivateKey getDevKeyForNetwork(String network) {
        File dir = new File(BASE_DEVKEYS_PATH + network);
        if(dir.exists() && dir.isDirectory()) {
            for( File f  : dir.listFiles()) {
                try {
                    return new PrivateKey(Do.read(f));
                } catch (Exception e) {

                }
            }
        }
        return null;
    }

    private static Client checkDevKeyForNetwork(PrivateKey key, String network) {
        try {
            Client client = new Client("http://node-1-"+network+".utoken.io", key, null);
            Binder res = client.getStats();
            if(res.containsKey("itemResult")) {
                return ((ItemResult)res.get("itemResult")).errors.isEmpty() ? client : null;
            }
            return client;
        } catch (IOException e) {
            return null;
        }

    }
}
