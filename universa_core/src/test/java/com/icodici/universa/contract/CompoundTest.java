/*
 * Copyright (c) 2018 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by WildCats <kalinkineo@gmail.com>, February 2018.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.helpers.Compound;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.node2.Quantiser;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Before;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;

public class CompoundTest {

    @Test
    public void basicTest() throws Exception {

        Contract contractWithSubItems = new Contract(TestKeys.privateKey(1));

        Contract newItem = new Contract(TestKeys.privateKey(2));
        newItem.seal();

        contractWithSubItems.addNewItems(newItem);

        Contract revokingItem = new Contract(TestKeys.privateKey(3));
        revokingItem.seal();

        contractWithSubItems.addRevokingItems(revokingItem);
        contractWithSubItems.seal();


        Contract contractWithReferencedItems = new Contract(TestKeys.privateKey(1));
        contractWithReferencedItems.seal();

        Contract refItem = new Contract(TestKeys.privateKey(4));
        refItem.seal();
        contractWithReferencedItems.getTransactionPack().addReferencedItem(refItem);


        Contract contractWithTags = new Contract(TestKeys.privateKey(1));
        contractWithTags.seal();
        contractWithTags.getTransactionPack().addTag("main",contractWithTags.getId());


        Compound compound = new Compound();
        compound.addContract("sub",contractWithSubItems,null);
        compound.addContract("ref",contractWithReferencedItems,Binder.of("foo","bar"));
        compound.addContract("tags",contractWithTags,null);
        Contract compoundContract = compound.getCompoundContract();

        compoundContract = Contract.fromPackedTransaction(compoundContract.getPackedTransaction());

        compound = new Compound(compoundContract);

        assertNull(compound.getContract("unknown"));
        assertEquals(compound.getContract("sub").getTransactionPack().getSubItems().size(),2);
        assertEquals(compound.getContract("ref").getTransactionPack().getReferencedItems().size(),1);
        assertEquals(compound.getContract("tags").getTransactionPack().getTags().size(),1);

    }

}