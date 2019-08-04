package com.icodici.universa.contract.helpers;

import com.icodici.crypto.KeyAddress;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.SimpleRole;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

public class SecuredLoanHelper {

    static final private String FIELD_REPAYMENT_AMOUNT = "repayment_amount";
    static final private String PATH_REPAYMENT_AMOUNT= "definition.data."+FIELD_REPAYMENT_AMOUNT;

    static final private String FIELD_MINTABLE_REPAYMENT_ISSUER = "repayment_issuer";
    static final private String PATH_MINTABLE_REPAYMENT_ISSUER= "definition.data."+FIELD_MINTABLE_REPAYMENT_ISSUER;

    static final private String FIELD_MINTABLE_REPAYMENT_CURRENCY = "repayment_currency";
    static final private String PATH_MINTABLE_REPAYMENT_CURRENCY= "definition.data."+FIELD_MINTABLE_REPAYMENT_CURRENCY;

    static final private String FIELD_FIXED_SUPPLY_REPAYMENT_ORIGIN = "repayment_origin";
    static final private String PATH_FIXED_SUPPLY_REPAYMENT_ORIGIN= "definition.data."+FIELD_FIXED_SUPPLY_REPAYMENT_ORIGIN;





    static final private String FIELD_REPAYMENT = "rep_id";
    static final private String PATH_REPAYMENT = "state.data."+FIELD_REPAYMENT;

    static final private String FIELD_COLLATERAL_ID = "col_id";
    static final private String PATH_COLLATERAL_ID = "state.data."+FIELD_COLLATERAL_ID;

    static final private String FIELD_VERSION = "version";

    static final public String FIELD_STATUS = "status";
    static final public String PATH_STATUS = "state.data."+FIELD_STATUS;
    static final public String FIELD_EXPIRES = "defaultAt";
    static final public String PATH_EXPIRES = "definition.data."+FIELD_EXPIRES;
    static final public String STATUS_INIT = "init";
    static final public String STATUS_IN_PROGRESS = "in_progress";
    static final public String STATUS_DEFAULT = "default";
    static final public String STATUS_REPAID = "repaid";
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

        Reference refClosedRepaymentCheck = new Reference(contract);
        refClosedRepaymentCheck.name = "refClosedRepaymentCheck";
        refClosedRepaymentCheck.type = Reference.TYPE_TRANSACTIONAL;
        refClosedRepaymentCheck.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==\""+loanContractId.toBase64String()+"\"",
                "ref."+PATH_STATUS+"==\""+STATUS_CLOSED+"\""
        )));

        contract.addReference(refClosedRepaymentCheck);

        SimpleRole owner = new SimpleRole("owner",Do.listOf(lenderAddress));
        owner.addRequiredReference("refClosedRepaymentCheck", Role.RequiredMode.ALL_OF);
        contract.registerRole(owner);
    }

    private static KeyAddress getLender(Contract securedLoan) {
        return securedLoan.getRole("lender").getSimpleAddress();
    }

    private static KeyAddress getBorrower(Contract securedLoan) {
        return securedLoan.getRole("borrower").getSimpleAddress();
    }

    private static Contract getCollateral(Contract securedLoan) {
        HashId id = HashId.withDigest((String) securedLoan.get(PATH_COLLATERAL_ID));
        if(securedLoan.get(PATH_STATUS).equals(STATUS_IN_PROGRESS)) {
            return securedLoan.getTransactionPack().getSubItem(id);
        } else if(securedLoan.get(PATH_STATUS).equals(STATUS_REPAID)) {
            return securedLoan.getTransactionPack().getReferencedItems().get(id);
        } else {
            throw new IllegalArgumentException("invalid secured loan contract state. must be in progress or repaid");
        }
    }


    private static Contract getRepayment(Contract securedLoan) {
        if(securedLoan.get(PATH_STATUS).equals(STATUS_REPAID)) {
            return securedLoan.getTransactionPack().getSubItem(HashId.withDigest((String) securedLoan.get(PATH_REPAYMENT)));
        } else {
            throw new IllegalArgumentException("invalid secured loan contract state. must be in repaid");
        }
    }

    /**
     * Prepares secured loan agreement contract and its satellites.
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
     * @return the array of contracts [secured loan agreement contract, loan contract owned by borrower]
     */
    public static Contract[] initSecuredLoan(Binder definitionData, KeyAddress lenderAddress, KeyAddress borrowerAddress, Contract loan, Duration loanDuration, Contract collateral, String repaymentAmount, boolean mintable, HashId repaymentOrigin, KeyAddress repaymentIssuer, String repaymentCurrency) {
        return initSecuredLoan(Do.listOf(lenderAddress,borrowerAddress),definitionData,lenderAddress,borrowerAddress,loan,loanDuration,collateral,repaymentAmount,mintable,repaymentOrigin,repaymentIssuer,repaymentCurrency);
    }

    /**
     * Prepares secured loan agreement contract and its satellites.
     *
     * Contract returned is not signed/registered. Must be signed (by borrower, lender and issuer keys) and registered to get its satellites registered and usable
     *
     * @param issuerKeys keys/addresses to set "issuer" of secured loan contract to.
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
     * @return the array of contracts [secured loan agreement contract, loan contract owned by borrower]
     */
    public static Contract[] initSecuredLoan(Collection<?> issuerKeys, Binder definitionData, KeyAddress lenderAddress, KeyAddress borrowerAddress, Contract loan, Duration loanDuration, Contract collateral, String repaymentAmount, boolean mintable, HashId repaymentOrigin, KeyAddress repaymentIssuer, String repaymentCurrency) {
        Contract securedLoan = new Contract();
        securedLoan.setExpiresAt(ZonedDateTime.now().plusYears(100));
        securedLoan.setIssuerKeys(issuerKeys);
        securedLoan.setOwnerKeys(lenderAddress,borrowerAddress);
        securedLoan.setCreatorKeys(lenderAddress,borrowerAddress);

        if(definitionData != null)
            securedLoan.getDefinition().getData().putAll(definitionData);

//        securedLoan.getDefinition().getData().put(FIELD_LENDER,lenderAddress.toString());
//        securedLoan.getDefinition().getData().put(FIELD_BORROWER,borrowerAddress.toString());
        securedLoan.getDefinition().getData().put(FIELD_VERSION,"2");

        SimpleRole lenderRole = new SimpleRole("lender",Do.listOf(lenderAddress));
        securedLoan.registerRole(lenderRole);

        SimpleRole borrowerRole = new SimpleRole("borrower",Do.listOf(borrowerAddress));
        securedLoan.registerRole(borrowerRole);

        //this role is used to compare to repayment contract owner for equality
        SimpleRole repaymentRole = new SimpleRole("repayment",Do.listOf(lenderAddress));
        repaymentRole.addRequiredReference("refClosedRepaymentCheck", Role.RequiredMode.ALL_OF);
        securedLoan.registerRole(repaymentRole);



        //MODIFY STATE DATA PERMISSIONS
        //INIT->IN_PROGRESS
        SimpleRole initRole = new SimpleRole("@init",Do.listOf(lenderAddress,borrowerAddress));
        initRole.addRequiredReference("refInit", Role.RequiredMode.ALL_OF);

        ModifyDataPermission initPermission =
                new ModifyDataPermission(initRole,
                        Binder.of("fields",Binder.of(
                                FIELD_STATUS,Do.listOf(STATUS_IN_PROGRESS),
                                "/references",null,
                                FIELD_COLLATERAL_ID, null
                        )));
        securedLoan.addPermission(initPermission);

        //IN_PROGRESS->DEFAULT
        SimpleRole defaultRole = new SimpleRole("@default",Do.listOf(lenderAddress));
        defaultRole.addRequiredReference("refDefault", Role.RequiredMode.ALL_OF);

        ModifyDataPermission defaultPermission =
                new ModifyDataPermission(defaultRole,
                        Binder.of("fields",Binder.of(FIELD_STATUS,Do.listOf(STATUS_DEFAULT))));
        securedLoan.addPermission(defaultPermission);



        //IN_PROGRESS->REPAID
        SimpleRole repaidRole = new SimpleRole("@repaid",Do.listOf(borrowerAddress));
        repaidRole.addRequiredReference("refRepayment", Role.RequiredMode.ALL_OF);
        repaidRole.addRequiredReference("refRepaid", Role.RequiredMode.ALL_OF);



        ModifyDataPermission repaidPermission =
                new ModifyDataPermission(repaidRole,
                        Binder.of("fields",Binder.of(
                                FIELD_STATUS,Do.listOf(STATUS_REPAID),
                                FIELD_REPAYMENT, null
                                )));
        securedLoan.addPermission(repaidPermission);


        //REPAID->CLOSED
        SimpleRole closedRole = new SimpleRole("@closed",Do.listOf(borrowerAddress,lenderAddress));
        closedRole.addRequiredReference("refClosed", Role.RequiredMode.ALL_OF);

        ModifyDataPermission closedPermission =
                new ModifyDataPermission(closedRole,
                        Binder.of("fields",Binder.of(FIELD_STATUS,Do.listOf(STATUS_CLOSED))));
        securedLoan.addPermission(closedPermission);


        securedLoan.getDefinition().getData().put(FIELD_EXPIRES,ZonedDateTime.now().plus(loanDuration));

        securedLoan.getDefinition().getData().put(FIELD_REPAYMENT_AMOUNT,repaymentAmount);
        if(mintable) {
            securedLoan.getDefinition().getData().put(FIELD_MINTABLE_REPAYMENT_CURRENCY, repaymentCurrency);
            securedLoan.getDefinition().getData().put(FIELD_MINTABLE_REPAYMENT_ISSUER, repaymentIssuer.toString());
        } else {
            securedLoan.getDefinition().getData().put(FIELD_FIXED_SUPPLY_REPAYMENT_ORIGIN, repaymentOrigin.toBase64String());
        }

        securedLoan.getStateData().put(FIELD_STATUS, STATUS_INIT);


        Reference refInit = new Reference(securedLoan);
        refInit.name = "refInit";
        refInit.type = Reference.TYPE_EXISTING_STATE;
        refInit.setConditions(Binder.of("all_of",
                Do.listOf(
                        "this."+PATH_STATUS+"==\""+STATUS_INIT+"\""
                )));
        securedLoan.addReference(refInit);


        //REGISTER 1ST REV of secured loan to get its origin
        securedLoan.seal();

        Contract secureLoanRoot = securedLoan;



        //transfer collateral to complex secured loan status dependant role
        collateral = collateral.createRevision();
        collateral.setCreatorKeys(borrowerAddress,lenderAddress);
        setCollateralOwnerAndRefs(collateral,securedLoan.getOrigin(),lenderAddress,borrowerAddress);
        collateral.seal();




        //transfer loan directry to borrower
        loan = loan.createRevision();
        loan.setCreatorKeys(borrowerAddress,lenderAddress);
        loan.setOwnerKey(borrowerAddress);
        loan.seal();


        securedLoan = securedLoan.createRevision();
        securedLoan.setCreatorKeys(lenderAddress,borrowerAddress);
        securedLoan.getStateData().put(FIELD_STATUS,STATUS_IN_PROGRESS);
        securedLoan.getStateData().put(FIELD_COLLATERAL_ID, collateral.getId().toBase64String());


        //this reference is used to compare to corresponding repayment contract reference for equality
        Reference refClosedRepaymentCheck = new Reference(securedLoan);
        refClosedRepaymentCheck.name = "refClosedRepaymentCheck";
        refClosedRepaymentCheck.type = Reference.TYPE_EXISTING_STATE;
        refClosedRepaymentCheck.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==\""+securedLoan.getOrigin().toBase64String()+"\"",
                "ref."+PATH_STATUS+"==\""+STATUS_CLOSED+"\""
        )));
        securedLoan.addReference(refClosedRepaymentCheck);


        //Reference to valid repayment contract
        Reference refRepayment = new Reference(securedLoan);
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
        securedLoan.addReference(refRepayment);


        //Reference reqiured to set secured loan contract to default
        Reference refDefault = new Reference(securedLoan);
        refDefault.name = "refDefault";
        refDefault.type = Reference.TYPE_EXISTING_STATE;
        refDefault.setConditions(Binder.of("all_of",
                Do.listOf(
                        "this."+PATH_STATUS+"==\""+STATUS_IN_PROGRESS+"\"",
                        "now>this."+PATH_EXPIRES
                )));
        securedLoan.addReference(refDefault);

        //Reference reqiured to set secured loan contract to repaid.
        // It checks that there is valid reference to repayment сontract
        // and that repayment owner role and its (owner) reference are set correctly
        Reference refRepaid = new Reference(securedLoan);
        refRepaid.name = "refRepaid";
        refRepaid.type = Reference.TYPE_EXISTING_STATE;
        refRepaid.setConditions(Binder.of("all_of",
                Do.listOf(
                        "this."+PATH_STATUS+"==\""+STATUS_IN_PROGRESS+"\"",
                        "refRepayment.owner==this.state.roles.repayment",
                        "refRepayment.transactional.references.refClosedRepaymentCheck==this.state.references.refClosedRepaymentCheck",
                        "now<this."+PATH_EXPIRES
                )));
        securedLoan.addReference(refRepaid);

        Reference refclosed = new Reference(securedLoan);
        refclosed.name = "refClosed";
        refclosed.type = Reference.TYPE_EXISTING_STATE;
        refclosed.setConditions(Binder.of("all_of",
                Do.listOf(
                        "this."+PATH_STATUS+"==\""+ STATUS_REPAID+"\""
                )));
        securedLoan.addReference(refclosed);

        securedLoan.addNewItems(collateral);
        //securedLoan.addNewItems(collateralTemplate);
        securedLoan.addNewItems(loan);
        securedLoan.addNewItems(secureLoanRoot);

        securedLoan.seal();

        return new Contract[] {securedLoan,loan};
    }


    /**
     * Transfers secured loan agreement contract into DEFAULT state and transfers collateral to lender.
     *
     * Contract returned is not signed/registered. Must be signed (by lender) and registered to get collateral registered and usable by lender
     *
     * @param securedLoan secured loan agreement contract
     * @return the array of contracts [secured loan agreement contract, collateral contract owner by lender]
     */

    public static Contract[] defaultSecuredLoan(Contract securedLoan) {
        if(!securedLoan.getStateData().get(FIELD_STATUS).equals(STATUS_IN_PROGRESS)) {
            throw new IllegalArgumentException("wrong secured loan state. expected " + STATUS_IN_PROGRESS + " found " + securedLoan.getStateData().get(FIELD_STATUS));
        }

        KeyAddress lenderAddress = getLender(securedLoan);
        Contract collateral = getCollateral(securedLoan);

        securedLoan = securedLoan.createRevision();
        securedLoan.setCreatorKeys(lenderAddress);
        securedLoan.getStateData().set(FIELD_STATUS,STATUS_DEFAULT);


        collateral = collateral.createRevision();
        collateral.setCreatorKeys(lenderAddress);
        collateral.setOwnerKeys(lenderAddress);
        collateral.seal();

        securedLoan.addNewItems(collateral);
        securedLoan.seal();

        return new Contract[]{ securedLoan,collateral};
    }

    /**
     * Transfers secured loan agreement contract into REPAID state (repayment and collateral aren't owned by neither lender nor borrower at this point).
     *
     * Contract returned is not signed/registered. Must be signed (by borrower and registered
     *
     * @param securedLoan secured loan agreement contract
     * @param repayment contract owned by burrower by this time
     * @return the array of contracts [secured loan agreement contract]
     */

    public static Contract[] repaySecuredLoan(Contract securedLoan, Contract repayment) {

        if(!securedLoan.getStateData().get(FIELD_STATUS).equals(STATUS_IN_PROGRESS)) {
            throw new IllegalArgumentException("wrong secured loan state. expected " + STATUS_IN_PROGRESS + " found " + securedLoan.getStateData().get(FIELD_STATUS));
        }


        Contract collateral = getCollateral(securedLoan);

        KeyAddress borrowerAddress = getBorrower(securedLoan);
        KeyAddress lenderAddress = getLender(securedLoan);
        repayment = repayment.createRevision();
        repayment.setCreatorKeys(borrowerAddress);
        setRepaymentOwnerAndRefs(repayment,securedLoan.getOrigin(),lenderAddress);
        repayment.seal();

        securedLoan = securedLoan.createRevision();
        securedLoan.setCreatorKeys(borrowerAddress);
        securedLoan.getStateData().set(FIELD_STATUS,STATUS_REPAID);
        securedLoan.addNewItems(repayment);
        securedLoan.getStateData().put(FIELD_REPAYMENT,repayment.getId().toBase64String());
        securedLoan.seal();
        securedLoan.getTransactionPack().addReferencedItem(collateral);

        return new Contract[] {securedLoan};
    }


    /**
     * Transfers secured loan agreement contract into CLOSED state (repayment becomes owned by lender and collateral by borrower).
     *
     * Contract returned is not signed/registered. Must be signed (by borrower and lender), service contract must be added as referenced item to transaction and transaction can be registered then
     *
     * @param securedLoan secured loan agreement contract
     * @return the array of contracts [secured loan agreement contract, repayment contract owner by lender, collateral contract owner by borrower]
     */

    public static Contract[] closeSecuredLoan(Contract securedLoan) {

        if(!securedLoan.getStateData().get(FIELD_STATUS).equals(STATUS_REPAID)) {
            throw new IllegalArgumentException("wrong secured loan state. expected " + STATUS_REPAID + " found " + securedLoan.getStateData().get(FIELD_STATUS));
        }

        Contract repayment = getRepayment(securedLoan);
        Contract collateral = getCollateral(securedLoan);

        KeyAddress borrowerAddress = getBorrower(securedLoan);
        KeyAddress lenderAddress = getLender(securedLoan);

        repayment = repayment.createRevision();
        repayment.setCreatorKeys(borrowerAddress,lenderAddress);
        repayment.setOwnerKeys(lenderAddress);

        collateral = collateral.createRevision();
        collateral.setCreatorKeys(borrowerAddress,lenderAddress);
        collateral.setOwnerKeys(borrowerAddress);



        securedLoan = securedLoan.createRevision();
        securedLoan.setCreatorKeys(borrowerAddress,lenderAddress);
        securedLoan.getStateData().set(FIELD_STATUS,STATUS_CLOSED);
        securedLoan.addNewItems(repayment);
        securedLoan.addNewItems(collateral);
        securedLoan.seal();

        return new Contract[] {securedLoan,repayment,collateral};
    }
}
