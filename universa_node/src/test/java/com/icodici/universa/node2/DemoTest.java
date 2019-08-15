package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.TestKeys;
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
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.junit.Assert.assertNull;

public class DemoTest {
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

    Main createMain(String name, boolean nolog) throws InterruptedException {
        return createMain(name, "", nolog);
    }

    Main createMain(String name, String postfix, boolean nolog) throws InterruptedException {
        String path = new File("src/test_node_config_v2" + postfix + "/" + name).getAbsolutePath();
        System.out.println(path);
        String[] args = new String[]{"--test", "--config", path, nolog ? "--nolog" : ""};

        List<Main> mm = new ArrayList<>();

        Thread thread = new Thread(() -> {
            try {
                Main m = new Main(args);
                try {
                    m.config.addTransactionUnitsIssuerKeyData(new KeyAddress("Zau3tT8YtDkj3UDBSznrWHAjbhhU4SXsfQLWDFsv5vw24TLn6s"));
                } catch (KeyAddress.IllegalAddressException e) {
                    e.printStackTrace();
                }

                try {
                    //m.config.getKeysWhiteList().add(new PublicKey(Do.read("./src/test_contracts/keys/u_key.public.unikey")));
                    m.config.getAddressesWhiteList().add(new KeyAddress(new PublicKey(Do.read("./src/test_contracts/keys/u_key.public.unikey")), 0, true));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //m.config.getKeysWhiteList().add(m.config.getUIssuerKey());
                m.waitReady();
                mm.add(m);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread.setName("Node Server: " + name);
        thread.start();

        while (mm.size() == 0) {
            Thread.sleep(100);
        }
        return mm.get(0);
    }


    public Contract[] makeTransfer(Contract token, BigDecimal amount, Contract fromAccount, Contract toAccount, Contract commissionAccount, Set<PrivateKey> keysToSignTransferWith, Client client) throws ClientError, Quantiser.QuantiserException {
        assertEquals(token.get(TOKEN_ACCOUNT_PATH), fromAccount.getId().toBase64String());
        assertEquals(fromAccount.get(ACC_COMMISSION_ACCOUNT_PATH), commissionAccount.getId().toBase64String());
        assertTrue(fromAccount.getOwner().isAllowedForKeys(keysToSignTransferWith));
        token = token.createRevision(keysToSignTransferWith);
        int partsCount = new BigDecimal(fromAccount.get(ACC_COMMISSION_PERCENT_PATH).toString()).compareTo(new BigDecimal("0")) > 0 ? 2 : 1;
        Contract[] parts = token.split(partsCount);
        Contract transfer = parts[0];
        Contract commission = partsCount > 1 ? parts[1] : null;

        transfer.getStateData().set(TOKEN_VALUE_FIELD, amount);
        transfer.getKeysToSignWith().clear();
        BigDecimal rest = new BigDecimal(token.getStateData().getString(TOKEN_VALUE_FIELD))
                .subtract(new BigDecimal(transfer.getStateData().getString(TOKEN_VALUE_FIELD)));

        if (commission != null) {
            commission.getStateData().set(TOKEN_VALUE_FIELD, new BigDecimal(fromAccount.get(ACC_COMMISSION_PERCENT_PATH).toString()).multiply(amount));
            commission.getKeysToSignWith().clear();
            rest = rest.subtract(new BigDecimal(commission.getStateData().getString(TOKEN_VALUE_FIELD)));
        }

        token.getStateData().set(TOKEN_VALUE_FIELD, rest);
        token.seal();
        token.getTransactionPack().addReferencedItem(fromAccount);
        assertTrue(token.check());


//        ItemResult ir = client.register(token.getPackedTransaction(), 8000);
//        assertEquals(ir.state,ItemState.APPROVED);


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
        ItemResult ir = client.register(batch.getPackedTransaction(), 8000);
        System.out.println(ir.errors);
        assertEquals(ir.state, ItemState.APPROVED);

        return new Contract[]{token, transfer, commission};
    }

    @Test
    public void demo() throws Exception {
        PrivateKey person1 = TestKeys.privateKey(1);
        PrivateKey person2 = TestKeys.privateKey(2);
        PrivateKey bank = TestKeys.privateKey(3);


        Contract commissionAccRur = new Contract(bank);
        Contract commissionAccEur = new Contract(bank);
        commissionAccRur.getStateData().put(ACC_TYPE_FIELD, ACC_TYPE_BANK);
        commissionAccEur.getStateData().put(ACC_TYPE_FIELD, ACC_TYPE_BANK);

        commissionAccRur.getStateData().put(ACC_CURRENCY_FIELD, ACC_CURRENCY_RUR);
        commissionAccEur.getStateData().put(ACC_CURRENCY_FIELD, ACC_CURRENCY_EUR);

        commissionAccRur.getStateData().put(ACC_COMMISSION_PERCENT_FIELD, "0.0");
        commissionAccEur.getStateData().put(ACC_COMMISSION_PERCENT_FIELD, "0.0");

        commissionAccRur.getStateData().put(ACC_COMMISSION_ACCOUNT_FIELD, null);
        commissionAccEur.getStateData().put(ACC_COMMISSION_ACCOUNT_FIELD, null);

        commissionAccRur.seal();
        commissionAccEur.seal();


        Contract account1rur = new Contract(person1);
        Contract account2rur = new Contract(person2);

        Contract account2eur = new Contract(person2);

        account1rur.getStateData().put(ACC_TYPE_FIELD, ACC_TYPE_PERSONAL);
        account2rur.getStateData().put(ACC_TYPE_FIELD, ACC_TYPE_PERSONAL);
//        account1eur.getStateData().put(ACC_TYPE_PATH,ACC_TYPE_PERSONAL);
        account2eur.getStateData().put(ACC_TYPE_FIELD, ACC_TYPE_PERSONAL);

        account1rur.getStateData().put(ACC_CURRENCY_FIELD, ACC_CURRENCY_RUR);
        account2rur.getStateData().put(ACC_CURRENCY_FIELD, ACC_CURRENCY_RUR);
//        account1eur.getStateData().put(ACC_CURRENCY_PATH,ACC_CURRENCY_EUR);
        account2eur.getStateData().put(ACC_CURRENCY_FIELD, ACC_CURRENCY_EUR);

        account1rur.getStateData().put(ACC_COMMISSION_PERCENT_FIELD, "0.1");
        account2rur.getStateData().put(ACC_COMMISSION_PERCENT_FIELD, "0.0");
//        account1eur.getStateData().put(ACC_COMMISSION_PERCENT_PATH,"0.0");
        account2eur.getStateData().put(ACC_COMMISSION_PERCENT_FIELD, "0.2");

        account1rur.getStateData().put(ACC_COMMISSION_ACCOUNT_FIELD, commissionAccRur.getId().toBase64String());
        account2rur.getStateData().put(ACC_COMMISSION_ACCOUNT_FIELD, commissionAccRur.getId().toBase64String());
//        account1eur.getStateData().put(ACC_COMMISSION_ACCOUNT_PATH,commissionAccEur.getId());
        account2eur.getStateData().put(ACC_COMMISSION_ACCOUNT_FIELD, commissionAccEur.getId().toBase64String());

        account1rur.seal();
        account2rur.seal();
//        account1eur.seal();
        account2eur.seal();


        Contract rurToken = new Contract(bank);


        SimpleRole tokenOwner = new SimpleRole("owner",rurToken);
        tokenOwner.addRequiredReference("canplayaccowner", Role.RequiredMode.ALL_OF);
        rurToken.addRole(tokenOwner);
        rurToken.getStateData().put("account", account1rur.getId().toBase64String());
        rurToken.getStateData().put(TOKEN_VALUE_FIELD, "100000");

        RoleLink rl = new RoleLink("@owner",rurToken, "owner");
        SplitJoinPermission sjp =
                new SplitJoinPermission(rl, Binder.of("field_name", TOKEN_VALUE_FIELD,
                        "join_match_fields", Do.listOf("state.origin", TOKEN_ACCOUNT_PATH)));
        rurToken.addPermission(sjp);


        ModifyDataPermission mdp =
                new ModifyDataPermission(rl, Binder.of("fields", Binder.of(TOKEN_ACCOUNT_FIELD, null)));
        rurToken.addPermission(mdp);


        Reference canplayaccowner = new Reference(rurToken);
        canplayaccowner.name = "canplayaccowner";
        List<String> conditions = new ArrayList<>();
        conditions.add("ref.id==this." + TOKEN_ACCOUNT_PATH);
        conditions.add("this can_play ref.owner");
        canplayaccowner.setConditions(Binder.of("all_of", conditions));
        rurToken.addReference(canplayaccowner);


        Reference refAccount = new Reference(rurToken);
        refAccount.name = "refAccount";
        refAccount.setConditions(Binder.of("all_of", Do.listOf("this." + TOKEN_ACCOUNT_PATH + " == ref.id")));
        rurToken.addReference(refAccount);

        Reference refParent = new Reference(rurToken);
        refParent.name = "refParent";
        refParent.setConditions(Binder.of("any_of", Do.listOf(
                "this.state.parent == ref.id",
                "this.state.parent undefined"
        )));
        rurToken.addReference(refParent);

        Reference refParentAccount = new Reference(rurToken);
        refParentAccount.name = "refParentAccount";
        refParentAccount.setConditions(Binder.of("any_of", Do.listOf(
                "refParent." + TOKEN_ACCOUNT_PATH + " == ref.id",
                "this.state.parent undefined"
        )));
        rurToken.addReference(refParentAccount);


        Reference transferCheck = new Reference(rurToken);
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
                                        //"ref."+TOKEN_VALUE_PATH+"::number == refParentAccount."+ACC_COMMISSION_PERCENT_PATH+"::number",
                                        "ref." + TOKEN_ACCOUNT_PATH + " == refParentAccount." + ACC_COMMISSION_ACCOUNT_PATH,
                                        "ref.transactional.data.transfer_id == this.id"

                                ))
                        ))
                ))
        )));
        rurToken.addReference(transferCheck);
        rurToken.seal();


        // init network in permanet mode
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), "_permanet", false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        main.config.getKeysWhiteList().add(TestKeys.publicKey(20));
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        assertTrue(main.config.isPermanetMode());
        for (int i = 1; i < 4; i++)
            assertTrue(mm.get(i).config.isPermanetMode());


        assertEquals(client.register(commissionAccEur.getPackedTransaction(), 8000).state, ItemState.APPROVED);
        assertEquals(client.register(commissionAccRur.getPackedTransaction(), 8000).state, ItemState.APPROVED);
        assertEquals(client.register(account1rur.getPackedTransaction(), 8000).state, ItemState.APPROVED);
        assertEquals(client.register(account2rur.getPackedTransaction(), 8000).state, ItemState.APPROVED);
        assertEquals(client.register(account2eur.getPackedTransaction(), 8000).state, ItemState.APPROVED);
        rurToken.getTransactionPack().addReferencedItem(account1rur);
        ItemResult ir = client.register(rurToken.getPackedTransaction(), 8000);
        System.out.println(ir.errors);
        assertEquals(ir.state, ItemState.APPROVED);


        Contract[] result = makeTransfer(rurToken, new BigDecimal("5000"), account1rur, account2rur, commissionAccRur, new HashSet<>(Do.listOf(person1)), client);
        //commmission exists
        assertNotNull(result[2]);

        result = makeTransfer(result[1], new BigDecimal("2000"), account2rur, account1rur, commissionAccRur, new HashSet<>(Do.listOf(person2)), client);
        //transfer w/o commission
        assertNull(result[2]);

        mm.forEach(m->m.shutdown());
    }
}
