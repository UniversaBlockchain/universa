package com.icodici.universa.contract.helpers;

import com.icodici.crypto.KeyAddress;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.sql.Ref;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

public class EscrowHelper {

    static final public String FIELD_VERSION = "version";
    static final public String FIELD_PAYMENT = "payment";


    static final public String FIELD_STATUS = "status";
    static final public String PATH_STATUS = "state.data."+FIELD_STATUS;

    static final public String STATUS_INIT = "init";
    static final public String STATUS_OPEN = "open";
    static final public String STATUS_CANCELED = "canceled";
    static final public String STATUS_COMPLETE = "complete";


    /**
     * Prepares escrow agreement contract and its satellites (payment contract with ownership that depends on status of escrow)
     *
     * Contract returned is not signed/registered. Must be signed (by customer, executor and arbitrator keys) and registered
     * @param issuerKeys public keys/addresses to issue escrow with
     * @param definitionData additional data to put into definition
     * @param customerAddress escrow customer
     * @param  executorAddress escrow executor
     * @param  arbitratorAddress escrow arbitrator
     * @param  payment payment contract. Must be owned by customer at this point.
     *
     * @return the array of contracts [escrow agreement contract]
     */
    public static Contract[] initEscrow(Collection<?> issuerKeys, Binder definitionData, KeyAddress customerAddress, KeyAddress executorAddress, KeyAddress arbitratorAddress, Contract payment) {
        Contract escrow = new Contract();
        escrow.setExpiresAt(ZonedDateTime.now().plusYears(5));
        escrow.setIssuerKeys(issuerKeys);
        escrow.setCreatorKeys(issuerKeys);

        if(definitionData != null)
            escrow.getDefinition().getData().putAll(definitionData);



        SimpleRole customerRole = new SimpleRole("customer",Do.listOf(customerAddress));
        escrow.registerRole(customerRole);

        SimpleRole executorRole = new SimpleRole("executor",Do.listOf(executorAddress));
        escrow.registerRole(executorRole);

        SimpleRole arbitratorRole = new SimpleRole("arbitrator",Do.listOf(arbitratorAddress));
        escrow.registerRole(arbitratorRole);



        ListRole cancelOrComplete = new ListRole("cancelOrComplete");
        cancelOrComplete.addRequiredReference("refEscrowOpen", Role.RequiredMode.ALL_OF);
        cancelOrComplete.addRole(new RoleLink("@customer","customer"));
        cancelOrComplete.addRole(new RoleLink("@executor","executor"));
        cancelOrComplete.addRole(new RoleLink("@arbitrator","arbitrator"));
        cancelOrComplete.setQuorum(2);
        escrow.registerRole(cancelOrComplete);


        escrow.getDefinition().getData().put(FIELD_VERSION,1);
        escrow.getStateData().put(FIELD_STATUS,STATUS_INIT);
        escrow.getStateData().put(FIELD_PAYMENT,null);





        ModifyDataPermission modifyOpenedStatusPermission =
                new ModifyDataPermission(new RoleLink("@mdp1","cancelOrComplete"),
                        Binder.of("fields",Binder.of(
                                FIELD_STATUS,Do.listOf(STATUS_CANCELED,STATUS_COMPLETE)
                        )));
        escrow.addPermission(modifyOpenedStatusPermission);


        SimpleRole modifyInitStatusRole = new SimpleRole("@mdp_opened");
        modifyInitStatusRole.addRequiredReference("refEscrowInit", Role.RequiredMode.ALL_OF);

        ModifyDataPermission modifyInitStatusPermission =
                new ModifyDataPermission(modifyInitStatusRole,
                        Binder.of("fields",Binder.of(
                                FIELD_STATUS,Do.listOf(STATUS_OPEN),
                                FIELD_PAYMENT,null
                        )));
        escrow.addPermission(modifyInitStatusPermission);



        Reference refEscrowInit = new Reference(escrow);
        refEscrowInit.type = Reference.TYPE_EXISTING_DEFINITION;
        refEscrowInit.name = "refEscrowInit";
        refEscrowInit.setConditions(Binder.of("all_of",Do.listOf("this."+PATH_STATUS+"==\""+STATUS_INIT+"\"")));
        escrow.addReference(refEscrowInit);

        Reference refEscrowOpen = new Reference(escrow);
        refEscrowOpen.type = Reference.TYPE_EXISTING_DEFINITION;
        refEscrowOpen.name = "refEscrowOpen";
        refEscrowOpen.setConditions(Binder.of("all_of",Do.listOf("this."+PATH_STATUS+"==\""+STATUS_OPEN+"\"")));
        escrow.addReference(refEscrowOpen);
        escrow.seal();
        Contract escrowRoot = escrow;

        escrow = escrowRoot.createRevision();
        escrow.getStateData().set(FIELD_STATUS,STATUS_OPEN);



        payment = payment.createRevision();
        payment.setCreatorKeys(customerAddress);

        Reference escrowCanceled = new Reference(payment);
        escrowCanceled.name = "escrowCanceled";
        escrowCanceled.type = Reference.TYPE_TRANSACTIONAL;
        escrowCanceled.setConditions(Binder.of("all_of",Do.listOf("ref.origin==\""+escrow.getOrigin().toBase64String()+"\"")));
        escrowCanceled.setConditions(Binder.of("all_of",Do.listOf("ref."+PATH_STATUS+"==\""+STATUS_CANCELED+"\"")));
        payment.addReference(escrowCanceled);

        Reference escrowComplete = new Reference(payment);
        escrowComplete.name = "escrowComplete";
        escrowComplete.type = Reference.TYPE_TRANSACTIONAL;
        escrowComplete.setConditions(Binder.of("all_of",Do.listOf("ref.origin==\""+escrow.getOrigin().toBase64String()+"\"")));
        escrowComplete.setConditions(Binder.of("all_of",Do.listOf("ref."+PATH_STATUS+"==\""+STATUS_COMPLETE+"\"")));
        payment.addReference(escrowComplete);

        Reference canPlayEscrowCustomer = new Reference(payment);
        canPlayEscrowCustomer.name = "canPlayEscrowCustomer";
        canPlayEscrowCustomer.type = Reference.TYPE_TRANSACTIONAL;
        canPlayEscrowCustomer.setConditions(Binder.of("all_of",Do.listOf("ref.origin==\""+escrow.getOrigin().toBase64String()+"\"")));
        canPlayEscrowCustomer.setConditions(Binder.of("all_of",Do.listOf("this can_play ref.state.roles.customer")));
        payment.addReference(canPlayEscrowCustomer);

        Reference canPlayEscrowExecutor = new Reference(payment);
        canPlayEscrowExecutor.name = "canPlayEscrowExecutor";
        canPlayEscrowExecutor.type = Reference.TYPE_TRANSACTIONAL;
        canPlayEscrowExecutor.setConditions(Binder.of("all_of",Do.listOf("ref.origin==\""+escrow.getOrigin().toBase64String()+"\"")));
        canPlayEscrowExecutor.setConditions(Binder.of("all_of",Do.listOf("this can_play ref.state.roles.executor")));
        payment.addReference(canPlayEscrowExecutor);


        SimpleRole moneyForCustomer = new SimpleRole("moneyForCustomer");
        moneyForCustomer.addRequiredReference("canPlayEscrowCustomer", Role.RequiredMode.ALL_OF);
        moneyForCustomer.addRequiredReference("escrowCanceled", Role.RequiredMode.ALL_OF);

        SimpleRole moneyForExecutor = new SimpleRole("moneyForExecutor");
        moneyForExecutor.addRequiredReference("canPlayEscrowExecutor", Role.RequiredMode.ALL_OF);
        moneyForExecutor.addRequiredReference("escrowComplete", Role.RequiredMode.ALL_OF);

        ListRole moneyOwner = new ListRole("owner");
        moneyOwner.setMode(ListRole.Mode.ANY);
        moneyOwner.addRole(moneyForCustomer);
        moneyOwner.addRole(moneyForExecutor);
        payment.registerRole(moneyOwner);
        payment.seal();

        escrow.getStateData().set(FIELD_PAYMENT,payment.getId().toBase64String());
        escrow.addNewItems(escrowRoot);
        escrow.addNewItems(payment);

        escrow.seal();

        return new Contract[] {escrow};
    }

    /**
     * Cancels escrow agreement and returns payment to customer
     *
     * Contract returned is not signed/registered. Must be signed (by at least two of three: customer, executor and arbitrator keys) and registered
     *
     * @param escrow escrow agreement contract
     *
     * @return the array of contracts [escrow agreement contract, payment contract owned by customer]
     */

    public static Contract[] cancelEscrow(Contract escrow) {
        Contract payment = getPayment(escrow);

        escrow = escrow.createRevision();
        escrow.registerRole(new RoleLink("creator","cancelOrComplete"));
        escrow.getStateData().set(FIELD_STATUS,STATUS_CANCELED);

        payment = payment.createRevision();

        SimpleRole moneyForCustomer = new SimpleRole("moneyForCustomer");
        moneyForCustomer.addRequiredReference("canPlayEscrowCustomer", Role.RequiredMode.ALL_OF);
        moneyForCustomer.addRequiredReference("escrowCanceled", Role.RequiredMode.ALL_OF);

        SimpleRole moneyForExecutor = new SimpleRole("moneyForExecutor");
        moneyForExecutor.addRequiredReference("canPlayEscrowExecutor", Role.RequiredMode.ALL_OF);
        moneyForExecutor.addRequiredReference("escrowComplete", Role.RequiredMode.ALL_OF);

        ListRole creator = new ListRole("creator");
        creator.setMode(ListRole.Mode.ANY);
        creator.addRole(moneyForCustomer);
        creator.addRole(moneyForExecutor);
        payment.registerRole(creator);
        payment.setOwnerKeys(escrow.getRole("customer").getSimpleAddress());
        payment.seal();

        escrow.addNewItems(payment);
        return new Contract[] {escrow,payment};
    }

    /**
     * Completes escrow agreement and transfers payment to executor
     *
     * Contract returned is not signed/registered. Must be signed (by at least two of three: customer, executor and arbitrator keys) and registered
     *
     * @param escrow escrow agreement contract
     *
     * @return the array of contracts [escrow agreement contract, payment contract owned by executor]
     */

    public static Contract[] completeEscrow(Contract escrow) {
        Contract payment = getPayment(escrow);

        escrow = escrow.createRevision();
        escrow.registerRole(new RoleLink("creator","cancelOrComplete"));
        escrow.getStateData().set(FIELD_STATUS,STATUS_COMPLETE);

        payment = payment.createRevision();

        SimpleRole moneyForCustomer = new SimpleRole("moneyForCustomer");
        moneyForCustomer.addRequiredReference("canPlayEscrowCustomer", Role.RequiredMode.ALL_OF);
        moneyForCustomer.addRequiredReference("escrowCanceled", Role.RequiredMode.ALL_OF);

        SimpleRole moneyForExecutor = new SimpleRole("moneyForExecutor");
        moneyForExecutor.addRequiredReference("canPlayEscrowExecutor", Role.RequiredMode.ALL_OF);
        moneyForExecutor.addRequiredReference("escrowComplete", Role.RequiredMode.ALL_OF);

        ListRole creator = new ListRole("creator");
        creator.setMode(ListRole.Mode.ANY);
        creator.addRole(moneyForCustomer);
        creator.addRole(moneyForExecutor);
        payment.registerRole(creator);
        payment.setOwnerKeys(escrow.getRole("executor").getSimpleAddress());
        payment.seal();

        escrow.addNewItems(payment);
        return new Contract[] {escrow,payment};
    }

    private static Contract getPayment(Contract escrow) {
        return escrow.getTransactionPack().getSubItem(HashId.withDigest(escrow.getStateData().getString(FIELD_PAYMENT)));
    }

}
