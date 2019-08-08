package com.icodici.universa.contract.helpers;

import com.icodici.crypto.KeyAddress;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.contract.permissions.ChangeOwnerPermission;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class EscrowHelper {

    static final public String FIELD_VERSION = "version";
    static final public String FIELD_PAYMENT = "payment";



    static final public String FIELD_CUSTOMER_ESCROW_INFO = "customer_escrow_info";
    static final public String FIELD_CONTRACTOR_ASSIGNMENT_INFO = "contractor_assignment_info";
    static final public String FIELD_CONTRACTOR_COMPLETION_INFO = "contractor_completion_info";




    static final public String FIELD_ESCROW_EXPIRES = "escrowExpires";
    static final public String PATH_ESCROW_EXPIRES = "definition.data."+FIELD_ESCROW_EXPIRES;


    static final public String FIELD_STATUS = "status";
    static final public String PATH_STATUS = "state.data."+FIELD_STATUS;

    static final public String STATUS_INIT = "init";
    static final public String STATUS_OPEN = "open";
    static final public String STATUS_ASSIGNED = "assigned";
    static final public String STATUS_CANCELED = "canceled";
    static final public String STATUS_COMPLETE = "completed";
    static final public String STATUS_CLOSED = "closed";


    /**
     * Prepares escrow agreement contract and its satellites (payment contract with ownership that depends on status of escrow)
     *
     * Contract returned is not signed/registered. Must be signed by issuer and customer
     *
     * @param issuerKeys public keys/addresses to issue escrow with
     * @param definitionData free-form data to put into loan contract definition.data section
     * @param escrowData escrow description data to put into definition
     * @param customerAddress escrow customer
     * @param arbitratorAddress escrow arbitrator
     *
     * @param storageServiceAddress storage service is responsible for holding revision of an escrow.
     *                              Applicable when only one of customer/contractor signatures is required
     *                              to change state of escrow contract. Used to avoid situations when new
     *                              revision registered by one of the sides is not being sent to the other
     * @param escrowDuration duration of an escrow
     * @param payment payment contract. Must be owned by customer at this point.
     * @return the array of contracts [escrow agreement contract]
     */
    public static Contract[] initEscrow(Collection<?> issuerKeys, Binder definitionData, Binder escrowData, KeyAddress customerAddress, KeyAddress arbitratorAddress, KeyAddress storageServiceAddress, Duration escrowDuration, Contract payment) {
        Contract escrow = new Contract();
        escrow.setExpiresAt(ZonedDateTime.now().plusYears(5));
        escrow.setIssuerKeys(issuerKeys);
        escrow.setCreatorKeys(issuerKeys);

        if(definitionData != null)
            escrow.getDefinition().getData().putAll(definitionData);

        escrow.getDefinition().getData().put(FIELD_CUSTOMER_ESCROW_INFO,escrowData);



        SimpleRole customerRole = new SimpleRole("customer",Do.listOf(customerAddress));
        escrow.registerRole(customerRole);

        RoleLink contractorRole = new RoleLink("contractor","owner");
        escrow.registerRole(contractorRole);

        SimpleRole arbitratorRole = new SimpleRole("arbitrator",Do.listOf(arbitratorAddress));
        escrow.registerRole(arbitratorRole);

        if(storageServiceAddress != null) {
            escrow.registerRole(new SimpleRole("storage_service",Do.listOf(storageServiceAddress)));
        }

        //owner role to be changed by customer to contractor key address upon assignment
        RoleLink ownerRole = new RoleLink("owner","customer");
        escrow.registerRole(ownerRole);


        ListRole listRole = new ListRole("two_of_three");
        listRole.setQuorum(2);
        listRole.addRole(new RoleLink("@cu","customer"));
        listRole.addRole(new RoleLink("@co","contractor"));
        listRole.addRole(new RoleLink("@ar","arbitrator"));
        escrow.registerRole(listRole);


        
        //OPEN
        RoleLink openEscrow = new RoleLink("open_escrow","customer");
        openEscrow.addRequiredReference("ref_escrow_init", Role.RequiredMode.ALL_OF);
        escrow.registerRole(openEscrow);

        //ASSIGN
        ListRole assignEscrow = new ListRole("assign_escrow");
        assignEscrow.setMode(ListRole.Mode.ALL);
        assignEscrow.addRole(new RoleLink("@cu","customer"));
        assignEscrow.addRole(new RoleLink("@co","contractor"));
        assignEscrow.addRequiredReference("ref_escrow_open", Role.RequiredMode.ALL_OF);
        escrow.registerRole(assignEscrow);

        //CANCEL
        ListRole cancelEscrow = new ListRole("cancel_escrow");
        cancelEscrow.setMode(ListRole.Mode.ANY);

        //Customer cancels open escrow
        RoleLink cancelsOpenEscrow = new RoleLink("customer_cancels","customer");
        cancelsOpenEscrow.addRequiredReference("ref_escrow_open", Role.RequiredMode.ALL_OF);

        //Customer cancels assigned escrow by timeout
        RoleLink cancelAssignedEscrowTimeout = new RoleLink("customer+timeout","customer");
        cancelAssignedEscrowTimeout.addRequiredReference("ref_escrow_assigned_timeout", Role.RequiredMode.ALL_OF);

        //Contractor+customer cancel assigned escrow anytime
        ListRole cancelAssignedEscrow = new ListRole("c+c");
        cancelAssignedEscrow.setMode(ListRole.Mode.ALL);
        cancelAssignedEscrow.addRole(new RoleLink("@co","contractor"));
        cancelAssignedEscrow.addRole(new RoleLink("@cu","customer"));
        cancelAssignedEscrow.addRequiredReference("ref_escrow_assigned", Role.RequiredMode.ALL_OF);

        //c+c or a+customer may cancel completed escrow
        ListRole cancelCompletedEscrow = new ListRole("cancel_completed_escrow");
        cancelCompletedEscrow.setMode(ListRole.Mode.ANY);

        ListRole cnc = new ListRole("c+c");
        cnc.setMode(ListRole.Mode.ALL);
        cnc.addRole(new RoleLink("@co","contractor"));
        cnc.addRole(new RoleLink("@cu","customer"));
        cancelCompletedEscrow.addRole(cnc);

        ListRole ancustomer = new ListRole("a+customer");
        ancustomer.setMode(ListRole.Mode.ALL);
        ancustomer.addRole(new RoleLink("@ar","arbitrator"));
        ancustomer.addRole(new RoleLink("@cu","customer"));
        cancelCompletedEscrow.addRole(ancustomer);


        cancelCompletedEscrow.addRequiredReference("ref_escrow_completed", Role.RequiredMode.ALL_OF);


        cancelEscrow.addRole(cancelsOpenEscrow);
        cancelEscrow.addRole(cancelAssignedEscrowTimeout);
        cancelEscrow.addRole(cancelAssignedEscrow);
        cancelEscrow.addRole(cancelCompletedEscrow);
        escrow.registerRole(cancelEscrow);

        //COMPLETE
        //Contractor completes assigned escrow before timeout
        ListRole completeEscrow = new ListRole("complete_escrow");
        completeEscrow.setMode(ListRole.Mode.ALL);
        completeEscrow.addRole(new RoleLink("@co","contractor"));
        if(escrow.getRole("storage_service") != null) {
            completeEscrow.addRole(new RoleLink("@ss","storage_service"));
        }
        completeEscrow.addRequiredReference("ref_escrow_assigned", Role.RequiredMode.ALL_OF);
        escrow.registerRole(completeEscrow);

        //CLOSE
        //2 of 3 close complete escrow
        ListRole closeEscrow = new ListRole("close_escrow");
        closeEscrow.setMode(ListRole.Mode.ANY);

        ListRole ancontractor = new ListRole("a+contractor");
        ancontractor.setMode(ListRole.Mode.ALL);
        ancontractor.addRole(new RoleLink("@co","contractor"));
        ancontractor.addRole(new RoleLink("@ar","arbitrator"));
        closeEscrow.addRole(ancontractor);

        ListRole customerAcceptsWork = new ListRole("customer_accepts_work");
        customerAcceptsWork.setMode(ListRole.Mode.ALL);
        customerAcceptsWork.addRole(new RoleLink("@cu","customer"));
        if(escrow.getRole("storage_service") != null) {
            customerAcceptsWork.addRole(new RoleLink("@ss","storage_service"));
        }
        closeEscrow.addRole(customerAcceptsWork);


        if(escrow.getRole("storage_service") != null) {
            completeEscrow.addRole(new RoleLink("@ss","storage_service"));
        }



        closeEscrow.addRequiredReference("ref_escrow_completed", Role.RequiredMode.ALL_OF);
        escrow.registerRole(closeEscrow);


        escrow.getDefinition().getData().put(FIELD_VERSION,1);
        escrow.getDefinition().getData().put(FIELD_ESCROW_EXPIRES,ZonedDateTime.now().plus(escrowDuration));
        escrow.getStateData().put(FIELD_STATUS, STATUS_INIT);
        escrow.getStateData().put(FIELD_PAYMENT,null);
        escrow.getStateData().put(FIELD_CONTRACTOR_ASSIGNMENT_INFO,null);
        escrow.getStateData().put(FIELD_CONTRACTOR_COMPLETION_INFO,null);



        Reference refEscrowInit = new Reference(escrow);
        refEscrowInit.type = Reference.TYPE_EXISTING_DEFINITION;
        refEscrowInit.name = "ref_escrow_init";
        refEscrowInit.setConditions(Binder.of("all_of",Do.listOf("this."+PATH_STATUS+"==\""+ STATUS_INIT +"\"")));
        escrow.addReference(refEscrowInit);

        Reference refEscrowOpen = new Reference(escrow);
        refEscrowOpen.type = Reference.TYPE_EXISTING_DEFINITION;
        refEscrowOpen.name = "ref_escrow_open";
        refEscrowOpen.setConditions(Binder.of("all_of",Do.listOf("this."+PATH_STATUS+"==\""+ STATUS_OPEN +"\"")));
        escrow.addReference(refEscrowOpen);

        Reference refEscrowAssigned = new Reference(escrow);
        refEscrowAssigned.type = Reference.TYPE_EXISTING_DEFINITION;
        refEscrowAssigned.name = "ref_escrow_assigned";
        refEscrowAssigned.setConditions(Binder.of("all_of",Do.listOf("this."+PATH_STATUS+"==\""+ STATUS_ASSIGNED +"\"","now < this."+PATH_ESCROW_EXPIRES)));
        escrow.addReference(refEscrowAssigned);


        Reference refEscrowAssignedTimeout = new Reference(escrow);
        refEscrowAssignedTimeout.type = Reference.TYPE_EXISTING_DEFINITION;
        refEscrowAssignedTimeout.name = "ref_escrow_assigned_timeout";
        refEscrowAssignedTimeout.setConditions(Binder.of("all_of",Do.listOf("this."+PATH_STATUS+"==\""+ STATUS_ASSIGNED +"\"","now > this."+PATH_ESCROW_EXPIRES)));
        escrow.addReference(refEscrowAssignedTimeout);


        Reference refEscrowComplete = new Reference(escrow);
        refEscrowComplete.type = Reference.TYPE_EXISTING_DEFINITION;
        refEscrowComplete.name = "ref_escrow_completed";
        refEscrowComplete.setConditions(Binder.of("all_of",Do.listOf("this."+PATH_STATUS+"==\""+ STATUS_COMPLETE +"\"")));
        escrow.addReference(refEscrowComplete);

        ModifyDataPermission openEscrowPemission = new ModifyDataPermission(new RoleLink("@oep","open_escrow"),
                Binder.of("fields",Binder.of(
                        FIELD_STATUS,Do.listOf(STATUS_OPEN),
                        FIELD_PAYMENT,null
                )));
        openEscrowPemission.setId("open_escrow");
        escrow.addPermission(openEscrowPemission);
        
        
        ModifyDataPermission assignEscrowPemission = new ModifyDataPermission(new RoleLink("@aep","assign_escrow"),
                Binder.of("fields",Binder.of(
                        FIELD_STATUS,Do.listOf(STATUS_ASSIGNED),
                        FIELD_CONTRACTOR_ASSIGNMENT_INFO,null
                )));
        assignEscrowPemission.setId("assign_escrow");
        escrow.addPermission(assignEscrowPemission);

        ModifyDataPermission cancelEscrowPemission = new ModifyDataPermission(new RoleLink("@canep","cancel_escrow"),
                Binder.of("fields",Binder.of(
                        FIELD_STATUS,Do.listOf(STATUS_CANCELED)
                )));
        cancelEscrowPemission.setId("cancel_escrow");
        escrow.addPermission(cancelEscrowPemission);

        ModifyDataPermission completeEscrowPemission = new ModifyDataPermission(new RoleLink("@comep","complete_escrow"),
                Binder.of("fields",Binder.of(
                        FIELD_STATUS,Do.listOf(STATUS_COMPLETE),
                        FIELD_CONTRACTOR_COMPLETION_INFO,null
                )));
        completeEscrowPemission.setId("complete_escrow");
        escrow.addPermission(completeEscrowPemission);

        ModifyDataPermission closeEscrowPemission = new ModifyDataPermission(new RoleLink("@cloep","close_escrow"),
                Binder.of("fields",Binder.of(
                        FIELD_STATUS,Do.listOf(STATUS_CLOSED)
                )));
        closeEscrowPemission.setId("close_escrow");
        escrow.addPermission(closeEscrowPemission);

        ChangeOwnerPermission assignContractorPermission = new ChangeOwnerPermission(new RoleLink("@cu","customer"));
        escrow.addPermission(assignContractorPermission);


        escrow.seal();
        Contract escrowInit = escrow;

        escrow = escrow.createRevision();
        escrow.setCreatorKeys(customerAddress);

        payment = payment.createRevision();
        payment.setCreatorKeys(escrow.getRole("customer").getSimpleAddress());

        Reference escrowCanceled = new Reference(payment);
        escrowCanceled.name = "escrow_canceled";
        escrowCanceled.type = Reference.TYPE_TRANSACTIONAL;
        escrowCanceled.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==\""+escrow.getOrigin().toBase64String()+"\"",
                "ref."+PATH_STATUS+"==\""+STATUS_CANCELED+"\"")));
        payment.addReference(escrowCanceled);

        Reference escrowComplete = new Reference(payment);
        escrowComplete.name = "escrow_closed";
        escrowComplete.type = Reference.TYPE_TRANSACTIONAL;
        escrowComplete.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==\""+escrow.getOrigin().toBase64String()+"\"",
                "ref."+PATH_STATUS+"==\""+ STATUS_CLOSED +"\""
        )));
        payment.addReference(escrowComplete);


        Reference canPlayEscrowCustomer = new Reference(payment);
        canPlayEscrowCustomer.name = "can_play_escrow_customer";
        canPlayEscrowCustomer.type = Reference.TYPE_TRANSACTIONAL;
        canPlayEscrowCustomer.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==\""+escrow.getOrigin().toBase64String()+"\"",
                "this can_play ref.state.roles.customer"
        )));
        payment.addReference(canPlayEscrowCustomer);

        Reference canPlayEscrowContractor = new Reference(payment);
        canPlayEscrowContractor.name = "can_play_escrow_contractor";
        canPlayEscrowContractor.type = Reference.TYPE_TRANSACTIONAL;
        canPlayEscrowContractor.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==\""+escrow.getOrigin().toBase64String()+"\"",
                "this can_play ref.state.roles.contractor"
        )));
        payment.addReference(canPlayEscrowContractor);


        SimpleRole moneyForCustomer = new SimpleRole("customer_takes_payment");
        moneyForCustomer.addRequiredReference("can_play_escrow_customer", Role.RequiredMode.ALL_OF);
        moneyForCustomer.addRequiredReference("escrow_canceled", Role.RequiredMode.ALL_OF);

        SimpleRole moneyForContractor = new SimpleRole("contractor_takes_payment");
        moneyForContractor.addRequiredReference("can_play_escrow_contractor", Role.RequiredMode.ALL_OF);
        moneyForContractor.addRequiredReference("escrow_closed", Role.RequiredMode.ALL_OF);

        ListRole moneyOwner = new ListRole("owner");
        moneyOwner.setMode(ListRole.Mode.ANY);
        moneyOwner.addRole(moneyForCustomer);
        moneyOwner.addRole(moneyForContractor);
        payment.registerRole(moneyOwner);
        payment.seal();

        escrow.getStateData().set(FIELD_PAYMENT,payment.getId().toBase64String());
        escrow.getStateData().set(FIELD_STATUS,STATUS_OPEN);
        escrow.addNewItems(payment);
        escrow.addNewItems(escrowInit);

        escrow.seal();

        return new Contract[]{escrow};
    }

    /**
     * Assigns escrow agreement contract to a contractor
     *
     * Contract returned is not signed/registered. Must be signed by customer and contractor
     *
     * @param escrow escrow agreement contract
     * @param contractorAddress contractor of an escrow
     * @param assignData free-form data from the contractor to put into escrow contract
     *
     * @return the array of contracts [escrow agreement contract]
     */

    public static Contract[] assignEscrow(Contract escrow, KeyAddress contractorAddress, Binder assignData) {
        Contract payment = getPayment(escrow);
        escrow = escrow.createRevision();

        escrow.setOwnerKeys(contractorAddress);

        ListRole creator = new ListRole("creator");
        creator.setMode(ListRole.Mode.ALL);
        creator.addRole(new RoleLink("@cu","customer"));
        creator.addRole(new RoleLink("@co","contractor"));
        escrow.registerRole(creator);

        escrow.getStateData().set(FIELD_STATUS, STATUS_ASSIGNED);
        escrow.getStateData().set(FIELD_CONTRACTOR_ASSIGNMENT_INFO,assignData);

        escrow.seal();
        escrow.getTransactionPack().addReferencedItem(payment);

        return new Contract[] {escrow};
    }




    /**
     * Cancels escrow agreement and returns payment to customer
     *
     * Contract returned is not signed/registered. Must be signed according to situation:
     * by customer - escrow is open or escrow is assigned and now expired,
     * by customer and contractor - escrow is assigned and not expired or escrow is complete
     * by customer and arbitrator - escrow is complete
     *
     * @param escrow escrow agreement contract
     * @return the array of contracts [escrow agreement contract, payment contract owned by customer]
     */

    public static Contract[] cancelEscrow(Contract escrow) {
        Contract payment = getPayment(escrow);
        escrow = escrow.createRevision();
        escrow.registerRole(new RoleLink("creator","customer"));
        escrow.getStateData().set(FIELD_STATUS,STATUS_CANCELED);

        payment = payment.createRevision();

        payment.setCreatorKeys(escrow.getRole("customer").getSimpleAddress());
        payment.setOwnerKeys(escrow.getRole("customer").getSimpleAddress());
        payment.seal();

        escrow.addNewItems(payment);

        escrow.seal();

        return new Contract[] {escrow,payment};
    }

    /**
     * Complete escrow agreement and add details on completion to state.data.contractor_completion_info
     *
     * Contract returned is not signed/registered. Must be signed by contractor and storage service (if available)
     *
     * @param escrow escrow agreement contract
     * @param completionData free-form data on escrow completion to put into escrow contract
     *
     * @return the array of contracts [escrow agreement contract]
     */

    public static Contract[] completeEscrow(Contract escrow, Binder completionData) {
        Contract payment = getPayment(escrow);

        escrow = escrow.createRevision();
        escrow.registerRole(new RoleLink("creator","contractor"));
        escrow.getStateData().set(FIELD_STATUS, STATUS_COMPLETE);
        escrow.getStateData().set(FIELD_CONTRACTOR_COMPLETION_INFO,completionData);
        escrow.seal();

        escrow.getTransactionPack().addReferencedItem(payment);

        return new Contract[] {escrow};
    }

    /**
     * Closes escrow agreement
     *
     * Contract returned is not signed/registered. Must be signed by customer and storage service (if available) or contractor and arbitrator
     *
     * @param escrow escrow agreement contract
     * @param creatorKeys public keys/addresses escrow closing will be signed by
     * @return the array of contracts [escrow agreement contract]
     */

    public static Contract[] closeEscrow(Contract escrow,Collection<?> creatorKeys) {
        Contract payment = getPayment(escrow);

        escrow = escrow.createRevision();
        escrow.registerRole(new SimpleRole("creator",creatorKeys));
        escrow.getStateData().set(FIELD_STATUS, STATUS_CLOSED);

        escrow.seal();

        escrow.getTransactionPack().addReferencedItem(payment);

        return new Contract[] {escrow};
    }

    /**
     * Transfers payment of closed escrow to contractor
     *
     * Contract returned is not signed/registered. Must be signed by contractor to register
     *
     * @param escrow escrow agreement contract
     * @return the array of contracts [payment contract owned by contractor]
     */

    public static Contract[] obtainPaymentOnClosedEscrow(Contract escrow) {
        Contract payment = getPayment(escrow);

        payment = payment.createRevision();
        KeyAddress contractor = escrow.getRole("contractor").getSimpleAddress();
        payment.setCreatorKeys(contractor);
        payment.setOwnerKeys(contractor);

        payment.seal();

        payment.getTransactionPack().addReferencedItem(escrow);

        return new Contract[] {payment};
    }

    private static Contract getPayment(Contract escrow) {
        HashId id = escrow.getStateData().get(FIELD_PAYMENT) != null ? HashId.withDigest(escrow.getStateData().getString(FIELD_PAYMENT)) : null;

        if(escrow.get(PATH_STATUS).equals(STATUS_OPEN)) {
            return escrow.getTransactionPack().getSubItem(id);
        } else if(escrow.get(PATH_STATUS).equals(STATUS_ASSIGNED) || escrow.get(PATH_STATUS).equals(STATUS_COMPLETE) || escrow.get(PATH_STATUS).equals(STATUS_CLOSED)) {
            return escrow.getTransactionPack().getReferencedItems().get(id);
        } else {
            return null;
        }
    }

}
