package com.icodici.universa.contract.helpers;

import com.icodici.crypto.KeyAddress;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.ItemState;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SecureLoanHelper {

    static final private String FIELD_REPAYMENT_AMOUNT = "repayment_amount";
    static final private String PATH_REPAYMENT_AMOUNT= "definition.data."+FIELD_REPAYMENT_AMOUNT;

    static final private String FIELD_MINTABLE_REPAYMENT_ISSUER = "repayment_issuer";
    static final private String PATH_MINTABLE_REPAYMENT_ISSUER= "definition.data."+FIELD_MINTABLE_REPAYMENT_ISSUER;

    static final private String FIELD_MINTABLE_REPAYMENT_CURRENCY = "repayment_currency";
    static final private String PATH_MINTABLE_REPAYMENT_CURRENCY= "definition.data."+FIELD_MINTABLE_REPAYMENT_CURRENCY;

    static final private String FIELD_FIXED_SUPPLY_REPAYMENT_ORIGIN = "repayment_origin";
    static final private String PATH_FIXED_SUPPLY_REPAYMENT_ORIGIN= "definition.data."+FIELD_FIXED_SUPPLY_REPAYMENT_ORIGIN;


    static final private String FIELD_REPAYMENT_TEMPLATE = "rep_template";
    static final private String PATH_REPAYMENT_TEMPLATE= "state.data."+FIELD_REPAYMENT_TEMPLATE;



    static final private String FIELD_REPAYMENT = "rep_id";
    static final private String PATH_REPAYMENT = "state.data."+FIELD_REPAYMENT;

    static final private String FIELD_COLLATERAL_ID = "col_id";
    static final private String PATH_COLLATERAL_ID = "state.data."+FIELD_COLLATERAL_ID;



    static final private String FIELD_LENDER = "lender";
    static final private String PATH_LENDER = "definition.data."+FIELD_LENDER;
    static final private String FIELD_BORROWER = "borrower";
    static final private String PATH_BORROWER = "definition.data."+FIELD_BORROWER;


    static final public String FIELD_STATUS = "status";
    static final public String PATH_STATUS = "state.data."+FIELD_STATUS;
    static final public String FIELD_EXPIRES = "defaultAt";
    static final public String PATH_EXPIRES = "definition.data."+FIELD_EXPIRES;
    static final public String STATUS_INIT = "init";
    static final public String STATUS_IN_PROGRESS = "in_progress";
    static final public String STATUS_DEFAULT = "default";
    static final public String STATUS_REPAID = "repayed";
    static final public String STATUS_CLOSED = "closed";



    private static void setCollateralOwnerAndRefs(Contract contract, HashId loanContractId, KeyAddress lenderAddress, KeyAddress borrowerAddress) {


        Reference refDefault = new Reference(contract);
        refDefault.name = "refDefault";
        refDefault.type = Reference.TYPE_TRANSACTIONAL;
        refDefault.setConditions(Binder.of("all_of", Do.listOf(
                "ref.origin==\""+loanContractId.toBase64String()+"\"",
                "ref."+PATH_STATUS+"==\""+STATUS_DEFAULT+"\""
        )));

        contract.addReference(refDefault);

        Reference refClosed = new Reference(contract);
        refClosed.name = "refClosed";
        refClosed.type = Reference.TYPE_TRANSACTIONAL;
        refClosed.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==\""+loanContractId.toBase64String()+"\"",
                "ref."+PATH_STATUS+"==\""+STATUS_CLOSED+"\""
        )));

        contract.addReference(refClosed);

        SimpleRole borrower = new SimpleRole("@b",Do.listOf(borrowerAddress));
        borrower.addRequiredReference("refClosed", Role.RequiredMode.ALL_OF);

        SimpleRole lender = new SimpleRole("@l",Do.listOf(lenderAddress));
        lender.addRequiredReference("refDefault", Role.RequiredMode.ALL_OF);

        ListRole owner = new ListRole("owner");
        owner.addRole(borrower);
        owner.addRole(lender);
        owner.setMode(ListRole.Mode.ANY);
        contract.registerRole(owner);

    }


    private static void setRepaymentOwnerAndRefs(Contract contract, HashId loanContractId, KeyAddress lenderAddress) {

        contract.getTransactionalData().set("is_repayment","yes");

        Reference refClosed = new Reference(contract);
        refClosed.name = "refClosed";
        refClosed.type = Reference.TYPE_TRANSACTIONAL;
        refClosed.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==\""+loanContractId.toBase64String()+"\"",
                "ref."+PATH_STATUS+"==\""+STATUS_CLOSED+"\""
        )));

        contract.addReference(refClosed);

        SimpleRole owner = new SimpleRole("owner",Do.listOf(lenderAddress));
        owner.addRequiredReference("refClosed", Role.RequiredMode.ALL_OF);
        contract.registerRole(owner);
    }

    private static KeyAddress getLender(Contract secureLoan) {
        try {
            return new KeyAddress((String) secureLoan.get(PATH_LENDER));
        } catch (KeyAddress.IllegalAddressException e) {
            throw  new IllegalStateException("invalid secure lLoan contract " + e.getMessage());
        }
    }

    private static KeyAddress getBorrower(Contract secureLoan) {
        try {
            return new KeyAddress((String) secureLoan.get(PATH_BORROWER));
        } catch (KeyAddress.IllegalAddressException e) {
            throw  new IllegalStateException("invalid secure lLoan contract " + e.getMessage());
        }
    }

    private static Contract getCollateral(Contract secureLoan) {
        HashId id = HashId.withDigest((String) secureLoan.get(PATH_COLLATERAL_ID));
        if(secureLoan.get(PATH_STATUS).equals(STATUS_IN_PROGRESS)) {
            return secureLoan.getTransactionPack().getSubItem(id);
        } else if(secureLoan.get(PATH_STATUS).equals(STATUS_REPAID)) {
            return secureLoan.getTransactionPack().getReferencedItems().get(id);
        } else {
            throw new IllegalArgumentException("invalid secure loan contract state. must be in progress or repaid");
        }
    }

    private static Contract getServiceContract(Contract secureLoan) {
        if(secureLoan.get(PATH_STATUS).equals(STATUS_IN_PROGRESS)) {
            return secureLoan.getTransactionPack().getSubItem(HashId.withDigest((String) secureLoan.get(PATH_REPAYMENT_TEMPLATE)));
        } else {
            throw new IllegalArgumentException("invalid secure loan contract state. must be in progress");
        }
    }

    private static Contract getRepayment(Contract secureLoan) {
        if(secureLoan.get(PATH_STATUS).equals(STATUS_REPAID)) {
            return secureLoan.getTransactionPack().getSubItem(HashId.withDigest((String) secureLoan.get(PATH_REPAYMENT)));
        } else {
            throw new IllegalArgumentException("invalid secure loan contract state. must be in repaid");
        }
    }

    /**
     * Prepares secure loan agreement contract and its satellites.
     *
     * Contract returned is not signed/registered. Must be signed (by borrower and lender) and registered to get its satellites registered and usable
     *
     * @param definitionData free-form data to put into loan contract definition.data section
     * @param lenderAddress address of lender key
     * @param borrowerAddress address of borrower key
     * @param loan contract that is given to borrower by lender. must be owned by lender by this time.
     * @param loanDuration duration of a loan
     * @param collateral contract that is acts as collateral for a loan must be owned by borrower by this time.
     * @param repaymentAmount the amount to be repaid at the end of the loan
     * @param mintable flag indicates if repayment is mintable token. Fixed supply otherwise
     * @param repaymentOrigin the expected origin of repayment token. passed for fixed supply tokens only. pass null otherwise.
     * @param repaymentIssuer the expected issuer of repayment token. passed for mintable tokens only. pass null otherwise.
     * @param repaymentCurrency the expected currency of repayment token. passed for mintable tokens only. pass null otherwise.
     * @return the array of contracts [secure loan agreement contract, loan contract owned by borrower]
     */

    public static Contract[] initSecureLoan(Binder definitionData, KeyAddress lenderAddress, KeyAddress borrowerAddress, Contract loan, Duration loanDuration, Contract collateral, String repaymentAmount, boolean mintable, HashId repaymentOrigin, KeyAddress repaymentIssuer, String repaymentCurrency) {
        Contract secureLoan = new Contract();
        secureLoan.setExpiresAt(ZonedDateTime.now().plusYears(100));
        secureLoan.setIssuerKeys(lenderAddress,borrowerAddress);
        secureLoan.setOwnerKeys(lenderAddress,borrowerAddress);
        secureLoan.setCreatorKeys(lenderAddress,borrowerAddress);

        if(definitionData != null)
            secureLoan.getDefinition().getData().putAll(definitionData);

        secureLoan.getDefinition().getData().put(FIELD_LENDER,lenderAddress.toString());
        secureLoan.getDefinition().getData().put(FIELD_BORROWER,borrowerAddress.toString());


        //MODIFY STATE DATA PERMISSIONS
        //INIT->IN_PROGRESS
        SimpleRole initRole = new SimpleRole("@init",Do.listOf(lenderAddress,borrowerAddress));
        initRole.addRequiredReference("refInit", Role.RequiredMode.ALL_OF);

        ModifyDataPermission initPermission =
                new ModifyDataPermission(initRole,
                        Binder.of("fields",Binder.of(
                                FIELD_STATUS,Do.listOf(STATUS_IN_PROGRESS),
                                "/references",null,
                                FIELD_COLLATERAL_ID, null,
                                FIELD_REPAYMENT_TEMPLATE, null
                        )));
        secureLoan.addPermission(initPermission);

        //IN_PROGRESS->DEFAULT
        SimpleRole defaultRole = new SimpleRole("@default",Do.listOf(lenderAddress));
        defaultRole.addRequiredReference("refDefault", Role.RequiredMode.ALL_OF);

        ModifyDataPermission defaultPermission =
                new ModifyDataPermission(defaultRole,
                        Binder.of("fields",Binder.of(FIELD_STATUS,Do.listOf(STATUS_DEFAULT))));
        secureLoan.addPermission(defaultPermission);



        //IN_PROGRESS->REPAID
        SimpleRole repaidRole = new SimpleRole("@repaid",Do.listOf(borrowerAddress));
        //these are here just to make them 'role reference'
        repaidRole.addRequiredReference("refRepayment", Role.RequiredMode.ALL_OF);
        repaidRole.addRequiredReference("refRepaymentTemplate", Role.RequiredMode.ALL_OF);
        //this is where the actual check happens
        repaidRole.addRequiredReference("refRepaid", Role.RequiredMode.ALL_OF);


        ModifyDataPermission repaidPermission =
                new ModifyDataPermission(repaidRole,
                        Binder.of("fields",Binder.of(
                                FIELD_STATUS,Do.listOf(STATUS_REPAID),
                                FIELD_REPAYMENT, null
                                )));
        secureLoan.addPermission(repaidPermission);


        //REPAID->CLOSED
        SimpleRole closedRole = new SimpleRole("@closed",Do.listOf(borrowerAddress,lenderAddress));
        closedRole.addRequiredReference("refClosed", Role.RequiredMode.ALL_OF);

        ModifyDataPermission closedPermission =
                new ModifyDataPermission(closedRole,
                        Binder.of("fields",Binder.of(FIELD_STATUS,Do.listOf(STATUS_CLOSED))));
        secureLoan.addPermission(closedPermission);


        secureLoan.getDefinition().getData().put(FIELD_EXPIRES,ZonedDateTime.now().plus(loanDuration).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"))));

        secureLoan.getDefinition().getData().put(FIELD_REPAYMENT_AMOUNT,repaymentAmount);
        if(mintable) {
            secureLoan.getDefinition().getData().put(FIELD_MINTABLE_REPAYMENT_CURRENCY, repaymentCurrency);
            secureLoan.getDefinition().getData().put(FIELD_MINTABLE_REPAYMENT_ISSUER, repaymentIssuer.toString());
        } else {
            secureLoan.getDefinition().getData().put(FIELD_FIXED_SUPPLY_REPAYMENT_ORIGIN, repaymentOrigin.toBase64String());
        }

        secureLoan.getStateData().put(FIELD_STATUS, STATUS_INIT);


        Reference refInit = new Reference(secureLoan);
        refInit.name = "refInit";
        refInit.type = Reference.TYPE_EXISTING_STATE;
        refInit.setConditions(Binder.of("all_of",
                Do.listOf(
                        "this."+PATH_STATUS+"==\""+STATUS_INIT+"\""
                )));
        secureLoan.addReference(refInit);


        //REGISTER 1ST REV of secure loan to get its origin
        secureLoan.seal();

        Contract secureLoanRoot = secureLoan;



        Contract repaymentTemplate = new Contract();
        repaymentTemplate.setExpiresAt(ZonedDateTime.now().plusYears(100));
        repaymentTemplate.setIssuerKeys(lenderAddress);
        repaymentTemplate.setCreatorKeys(lenderAddress);
        setRepaymentOwnerAndRefs(repaymentTemplate,secureLoan.getOrigin(),lenderAddress);
        repaymentTemplate.seal();



        //transfer collateral to complex secure loan status dependant role
        collateral = collateral.createRevision();
        collateral.setCreatorKeys(borrowerAddress,lenderAddress);
        setCollateralOwnerAndRefs(collateral,secureLoan.getOrigin(),lenderAddress,borrowerAddress);
        collateral.seal();




        //transfer loan directry to borrower
        loan = loan.createRevision();
        loan.setCreatorKeys(borrowerAddress,lenderAddress);
        loan.setOwnerKey(borrowerAddress);
        loan.seal();


        secureLoan = secureLoan.createRevision();
        secureLoan.setCreatorKeys(lenderAddress,borrowerAddress);
        secureLoan.getStateData().put(FIELD_STATUS,STATUS_IN_PROGRESS);
        secureLoan.getStateData().put(FIELD_REPAYMENT_TEMPLATE,repaymentTemplate.getId().toBase64String());
        secureLoan.getStateData().put(FIELD_COLLATERAL_ID, collateral.getId().toBase64String());


        Reference refRepaymentTemplate = new Reference(secureLoan);
        refRepaymentTemplate.name = "refRepaymentTemplate";
        refRepaymentTemplate.type = Reference.TYPE_EXISTING_STATE;
        refRepaymentTemplate.setConditions(Binder.of("all_of",Do.listOf("ref.id==this."+PATH_REPAYMENT_TEMPLATE)));
        secureLoan.addReference(refRepaymentTemplate);



        //Reference to repayment
        Reference refRepayment = new Reference(secureLoan);
        refRepayment.name = "refRepayment";
        refRepayment.type = Reference.TYPE_EXISTING_STATE;
        List<Object> conditions = Do.listOf(
                "ref.state.data.amount==this." + PATH_REPAYMENT_AMOUNT,
                "ref.transactional.data.is_repayment==\"yes\""
        );
        if(mintable) {
            conditions.add("ref.issuer==this."+PATH_MINTABLE_REPAYMENT_ISSUER);
            conditions.add("ref.definition.data.currency==this."+PATH_MINTABLE_REPAYMENT_CURRENCY);
        } else {
            conditions.add("ref.origin==this."+PATH_FIXED_SUPPLY_REPAYMENT_ORIGIN);
        }
        refRepayment.setConditions(Binder.of("all_of",conditions));
        secureLoan.addReference(refRepayment);


        Reference refDefault = new Reference(secureLoan);
        refDefault.name = "refDefault";
        refDefault.type = Reference.TYPE_EXISTING_STATE;
        refDefault.setConditions(Binder.of("all_of",
                Do.listOf(
                        "this."+PATH_STATUS+"==\""+STATUS_IN_PROGRESS+"\"",
                        "now>this."+PATH_EXPIRES
                )));
        secureLoan.addReference(refDefault);

        Reference refRepaid = new Reference(secureLoan);
        refRepaid.name = "refRepaid";
        refRepaid.type = Reference.TYPE_EXISTING_STATE;
        refRepaid.setConditions(Binder.of("all_of",
                Do.listOf(
                        "this."+PATH_STATUS+"==\""+STATUS_IN_PROGRESS+"\"",
                        "refRepayment.owner==refRepaymentTemplate.owner",
                        "refRepayment.transactional.references.refClosed==refRepaymentTemplate.transactional.references.refClosed",
                        "now<this."+PATH_EXPIRES
                )));
        secureLoan.addReference(refRepaid);

        Reference refclosed = new Reference(secureLoan);
        refclosed.name = "refClosed";
        refclosed.type = Reference.TYPE_EXISTING_STATE;
        refclosed.setConditions(Binder.of("all_of",
                Do.listOf(
                        "this."+PATH_STATUS+"==\""+ STATUS_REPAID+"\""
                )));
        secureLoan.addReference(refclosed);

        secureLoan.addNewItems(collateral);
        //secureLoan.addNewItems(collateralTemplate);
        secureLoan.addNewItems(repaymentTemplate);
        secureLoan.addNewItems(loan);
        secureLoan.addNewItems(secureLoanRoot);

        secureLoan.seal();

        return new Contract[] {secureLoan,loan};
    }


    /**
     * Transfers secure loan agreement contract into DEFAULT state and transfers collateral to lender.
     *
     * Contract returned is not signed/registered. Must be signed (by lender) and registered to get collateral registered and usable by lender
     *
     * @param secureLoan secure loan agreement contract
     * @return the array of contracts [secure loan agreement contract, collateral contract owner by lender]
     */

    public static Contract[] defaultSecureLoan(Contract secureLoan) {
        if(!secureLoan.getStateData().get(FIELD_STATUS).equals(STATUS_IN_PROGRESS)) {
            throw new IllegalArgumentException("wrong secure loan state. expected " + STATUS_IN_PROGRESS + " found " + secureLoan.getStateData().get(FIELD_STATUS));
        }

        KeyAddress lenderAddress = getLender(secureLoan);
        Contract collateral = getCollateral(secureLoan);

        secureLoan = secureLoan.createRevision();
        secureLoan.setCreatorKeys(lenderAddress);
        secureLoan.getStateData().set(FIELD_STATUS,STATUS_DEFAULT);


        collateral = collateral.createRevision();
        collateral.setCreatorKeys(lenderAddress);
        collateral.setOwnerKeys(lenderAddress);
        collateral.seal();

        secureLoan.addNewItems(collateral);
        secureLoan.seal();

        return new Contract[]{ secureLoan,collateral};
    }

    /**
     * Transfers secure loan agreement contract into REPAID state (repayment and collateral aren't owned by neither lender nor borrower at this point).
     *
     * Contract returned is not signed/registered. Must be signed (by borrower and registered
     *
     * @param secureLoan secure loan agreement contract
     * @param repayment contract owned by burrower by this time
     * @return the array of contracts [secure loan agreement contract]
     */

    public static Contract[] repaySecureLoan(Contract secureLoan, Contract repayment) {

        if(!secureLoan.getStateData().get(FIELD_STATUS).equals(STATUS_IN_PROGRESS)) {
            throw new IllegalArgumentException("wrong secure loan state. expected " + STATUS_IN_PROGRESS + " found " + secureLoan.getStateData().get(FIELD_STATUS));
        }


        Contract serviceContract = getServiceContract(secureLoan);
        Contract collateral = getCollateral(secureLoan);

        KeyAddress borrowerAddress = getBorrower(secureLoan);
        KeyAddress lenderAddress = getLender(secureLoan);
        repayment = repayment.createRevision();
        repayment.setCreatorKeys(borrowerAddress);
        setRepaymentOwnerAndRefs(repayment,secureLoan.getOrigin(),lenderAddress);
        repayment.seal();

        secureLoan = secureLoan.createRevision();
        secureLoan.setCreatorKeys(borrowerAddress);
        secureLoan.getStateData().set(FIELD_STATUS,STATUS_REPAID);
        secureLoan.addNewItems(repayment);
        secureLoan.getStateData().put(FIELD_REPAYMENT,repayment.getId().toBase64String());
        secureLoan.seal();
        secureLoan.getTransactionPack().addReferencedItem(serviceContract);
        secureLoan.getTransactionPack().addReferencedItem(collateral);

        return new Contract[] {secureLoan};
    }


    /**
     * Transfers secure loan agreement contract into CLOSED state (repayment becomes owned by lender and collateral by borrower).
     *
     * Contract returned is not signed/registered. Must be signed (by borrower and lender), service contract must be added as referenced item to transaction and transaction can be registered then
     *
     * @param secureLoan secure loan agreement contract
     * @return the array of contracts [secure loan agreement contract, repayment contract owner by lender, collateral contract owner by borrower]
     */

    public static Contract[] closeSecureLoan(Contract secureLoan) {

        if(!secureLoan.getStateData().get(FIELD_STATUS).equals(STATUS_REPAID)) {
            throw new IllegalArgumentException("wrong secure loan state. expected " + STATUS_REPAID + " found " + secureLoan.getStateData().get(FIELD_STATUS));
        }

        Contract repayment = getRepayment(secureLoan);
        Contract collateral = getCollateral(secureLoan);

        KeyAddress borrowerAddress = getBorrower(secureLoan);
        KeyAddress lenderAddress = getLender(secureLoan);

        repayment = repayment.createRevision();
        repayment.setCreatorKeys(borrowerAddress,lenderAddress);
        repayment.setOwnerKeys(lenderAddress);

        collateral = collateral.createRevision();
        collateral.setCreatorKeys(borrowerAddress,lenderAddress);
        collateral.setOwnerKeys(borrowerAddress);



        secureLoan = secureLoan.createRevision();
        secureLoan.setCreatorKeys(borrowerAddress,lenderAddress);
        secureLoan.getStateData().set(FIELD_STATUS,STATUS_CLOSED);
        secureLoan.addNewItems(repayment);
        secureLoan.addNewItems(collateral);
        secureLoan.seal();

        return new Contract[] {secureLoan,repayment,collateral};
    }
}
