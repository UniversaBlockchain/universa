package com.icodici.universa.contract;

import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.roles.Role;
import com.icodici.crypto.KeyAddress;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializable;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.utils.Bytes;

import java.util.*;
import static com.icodici.universa.contract.Reference.conditionsModeType.all_of;
import static com.icodici.universa.contract.Reference.conditionsModeType.any_of;

public class Reference implements BiSerializable {

    enum conditionsModeType {
        all_of,
        any_of,
        simple_condition
    }

    public String name = "";
    public int type = TYPE_EXISTING;
    public String transactional_id = "";
    public HashId contract_id = null;
    public boolean required = true;
    public HashId origin = null;
    public List<Role> signed_by = new ArrayList<>();
    public List<String> fields = new ArrayList<>();
    public List<String> roles = new ArrayList<>();
    public List<Approvable> matchingItems = new ArrayList<>();
    private Binder conditions = new Binder();
    private Contract baseContract;

    public static final int TYPE_TRANSACTIONAL = 1;
    public static final int TYPE_EXISTING = 2;

    public Reference() {}

    public Reference(Contract contract) {
        baseContract = contract;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        this.name = data.getString("name", null);

        this.type = data.getInt("type", null);

        this.transactional_id = data.getString("transactional_id", "");

//        String str_contract_id = data.getString("contract_id", null);
//        if (str_contract_id != null)
//            this.contract_id = HashId.withDigest(Bytes.fromHex(str_contract_id).getData());
//        else
//            this.contract_id = null;
//
//        this.required = data.getBoolean("required", true);
//
//        String str_origin = data.getString("origin", null);
//        if (str_origin != null)
//            this.origin = HashId.withDigest(Bytes.fromHex(str_origin).getData());
//        else
//            this.origin = null;

        this.contract_id = deserializer.deserialize(data.get("contract_id"));
        this.origin = deserializer.deserialize(data.get("origin"));

        this.signed_by = deserializer.deserializeCollection(data.getList("signed_by", new ArrayList<>()));


        List<String> roles = data.getList("roles", null);
        if (roles != null) {
            this.roles.clear();
            roles.forEach(this::addRole);
        }

        List<String> fields = data.getList("fields", null);
        if (fields != null) {
            this.fields.clear();
            fields.forEach(this::addField);
        }

        conditions = data.getBinder("where");
    }

    @Override
    public Binder serialize(BiSerializer s) {
        Binder data = new Binder();
        data.set("name", s.serialize(this.name));
        data.set("type", s.serialize(this.type));
        data.set("transactional_id", s.serialize(this.transactional_id));
//        if (this.contract_id != null)
//            data.set("contract_id", s.serialize(Bytes.toHex(this.contract_id.getDigest())));
//        data.set("required", s.serialize(this.required));
//        if (this.origin != null)
//            data.set("origin", s.serialize(Bytes.toHex(this.origin.getDigest())));
        if (this.contract_id != null)
            data.set("contract_id", s.serialize(this.contract_id));
        data.set("required", s.serialize(this.required));
        if (this.origin != null)
            data.set("origin", s.serialize(this.origin));
        data.set("signed_by", s.serialize(signed_by));

        data.set("roles", s.serialize(this.roles));
        data.set("fields", s.serialize(this.fields));

        data.set("where", s.serialize(conditions));

        return data;
    }

    public boolean equals(Reference a) {
        Binder dataThis = serialize(new BiSerializer());
        Binder dataA = a.serialize(new BiSerializer());
        return dataThis.equals(dataA);
    }

    final String[] operators = {" defined"," undefined","<=",">=","<",">","!=","=="," matches "};

    final int DEFINED = 0;
    final int UNDEFINED = 1;
    final int LESS_OR_EQUAL = 2;
    final int MORE_OR_EQUAL = 3;
    final int LESS = 4;
    final int MORE = 5;
    final int NOT_EQUAL = 6;
    final int EQUAL = 7;
    final int MATCHES = 8;

    enum compareOperandType {
        FIELD,
        CONSTSTR,
        CONSTOTHER
    }

    /**
     *The comparison method for finding reference contract
     *
     * @param refContract contract to check for matching
     * @param leftOperand field_selector
     * @param rightOperand right operand  (constant | field_selector), constant = ("null" | number | string | true | false)
     * @param typeOfRightOperand type of right operand (constant | field_selector), constant = ("null" | number | string | true | false)
     * @param indxOperator index operator in array of operators
     * @param contracts contract list to check for matching
     * @param iteration check inside references iteration number
     * @return true if match or false
     */
    public boolean compareOperands(Contract refContract,
                                   String leftOperand,
                                   String rightOperand,
                                   compareOperandType typeOfRightOperand,
                                   int indxOperator,
                                   Collection<Contract> contracts,
                                   int iteration)
    {
        boolean ret = false;
        Contract leftOperandContract = null;
        Contract rightOperandContract = null;
        int firstPointPos;

        if (leftOperand.startsWith("ref.")) {
            leftOperand = leftOperand.substring(4);
            leftOperandContract = refContract;
        } else if (leftOperand.startsWith("this.")) {
            if (baseContract == null)
                throw new IllegalArgumentException("Use left operand in condition: " + leftOperand + ". But this contract not initialized.");

            leftOperand = leftOperand.substring(5);
            leftOperandContract = baseContract;
        } else if ((firstPointPos = leftOperand.indexOf(".")) > 0) {
            if (baseContract == null)
                throw new IllegalArgumentException("Use left operand in condition: " + leftOperand + ". But this contract not initialized.");

            Reference ref = baseContract.findReferenceByName(leftOperand.substring(0, firstPointPos));
            if (ref == null)
                throw new IllegalArgumentException("Not found reference: " + leftOperand.substring(0, firstPointPos));

            for (Contract checkedContract: contracts)
                if (ref.isMatchingWith(checkedContract, contracts, iteration + 1))
                    leftOperandContract = checkedContract;

            if (leftOperandContract == null)
                return false;

            leftOperand = leftOperand.substring(firstPointPos + 1);
        }
        else
            throw new IllegalArgumentException("Invalid format of left operand in condition: " + leftOperand + ". Missing contract field.");

        if (rightOperand != null) {     // if != null, rightOperand then FIELD or CONSTANT
            if (typeOfRightOperand == compareOperandType.FIELD) {     // if typeOfRightOperand - FIELD
                if (rightOperand.startsWith("ref.")) {
                    rightOperand = rightOperand.substring(4);
                    rightOperandContract = refContract;
                } else if (rightOperand.startsWith("this.")) {
                    if (baseContract == null)
                        throw new IllegalArgumentException("Use right operand in condition: " + rightOperand + ". But this contract not initialized.");

                    rightOperand = rightOperand.substring(5);
                    rightOperandContract = baseContract;
                } else if ((firstPointPos = rightOperand.indexOf(".")) > 0) {
                    if (baseContract == null)
                        throw new IllegalArgumentException("Use right operand in condition: " + rightOperand + ". But this contract not initialized.");

                    Reference ref = baseContract.findReferenceByName(rightOperand.substring(0, firstPointPos));
                    if (ref == null)
                        throw new IllegalArgumentException("Not found reference: " + rightOperand.substring(0, firstPointPos));

                    for (Contract checkedContract: contracts)
                        if (ref.isMatchingWith(checkedContract, contracts, iteration + 1))
                            rightOperandContract = checkedContract;

                    if (rightOperandContract == null)
                        return false;

                    rightOperand = rightOperand.substring(firstPointPos + 1);
                }
                else
                    throw new IllegalArgumentException("Invalid format of right operand in condition: " + rightOperand + ". Missing contract field.");
            }

            try {
                switch (indxOperator) {
                    case LESS:
                    case MORE:
                    case LESS_OR_EQUAL:
                    case MORE_OR_EQUAL:
                        if (typeOfRightOperand == compareOperandType.FIELD) {               // rightOperand is FIELD
                            if (((indxOperator == LESS) && ((int) leftOperandContract.get(leftOperand) < (int) rightOperandContract.get(rightOperand))) ||
                                ((indxOperator == MORE) && ((int) leftOperandContract.get(leftOperand) > (int) rightOperandContract.get(rightOperand))) ||
                                ((indxOperator == LESS_OR_EQUAL) && ((int) leftOperandContract.get(leftOperand) <= (int) rightOperandContract.get(rightOperand))) ||
                                ((indxOperator == MORE_OR_EQUAL) && ((int) leftOperandContract.get(leftOperand) >= (int) rightOperandContract.get(rightOperand))))
                                ret = true;
                        } else if (typeOfRightOperand == compareOperandType.CONSTOTHER) {              //rightOperand is CONSTANT (null | number | true | false)
                            if ((rightOperand != "null") && (rightOperand != "false") && (rightOperand != "true"))
                                if (((indxOperator == LESS) && ((int) leftOperandContract.get(leftOperand) < Integer.parseInt(rightOperand))) ||
                                    ((indxOperator == MORE) && ((int) leftOperandContract.get(leftOperand) > Integer.parseInt(rightOperand))) ||
                                    ((indxOperator == LESS_OR_EQUAL) && ((int) leftOperandContract.get(leftOperand) <= Integer.parseInt(rightOperand))) ||
                                    ((indxOperator == MORE_OR_EQUAL) && ((int) leftOperandContract.get(leftOperand) >= Integer.parseInt(rightOperand))))
                                    ret = true;
                        } else
                            throw new IllegalArgumentException("Invalid operator for string in condition: " + operators[indxOperator]);

                        break;

                    case NOT_EQUAL:
                    case EQUAL:
                        if (typeOfRightOperand == compareOperandType.FIELD) {         // rightOperand is FIELD
                            if (((indxOperator == NOT_EQUAL) &&
                                ((leftOperandContract.get(leftOperand) != rightOperandContract.get(rightOperand)) ||
                                 (!leftOperandContract.get(leftOperand).equals(rightOperandContract.get(rightOperand))))) ||
                                ((indxOperator == EQUAL) &&
                                ((leftOperandContract.get(leftOperand) == rightOperandContract.get(rightOperand)) ||
                                 (leftOperandContract.get(leftOperand).equals(rightOperandContract.get(rightOperand))))))
                                ret = true;
                        } else if (typeOfRightOperand == compareOperandType.CONSTOTHER) {           //rightOperand is CONSTANT (null|number|true|false)
                            if ((rightOperand != "null") && (rightOperand != "false") && (rightOperand != "true")) {
                                if (((indxOperator == NOT_EQUAL) && ((int) leftOperandContract.get(leftOperand) != Integer.parseInt(rightOperand))) ||
                                    ((indxOperator == EQUAL) && ((int) leftOperandContract.get(leftOperand) == Integer.parseInt(rightOperand))))
                                    ret = true;
                            } else {          //if rightOperand : null|false|true
                                if (((indxOperator == NOT_EQUAL) &&
                                    (((rightOperand == "null") && (leftOperandContract.get(leftOperand) != null)) ||
                                     ((rightOperand == "true") && ((boolean) leftOperandContract.get(leftOperand) != true)) ||
                                     ((rightOperand == "false") && ((boolean) leftOperandContract.get(leftOperand) != false))))
                                    || ((indxOperator == EQUAL) &&
                                    (((rightOperand == "null") && (leftOperandContract.get(leftOperand) == null)) ||
                                     ((rightOperand == "true") && ((boolean) leftOperandContract.get(leftOperand) == true)) ||
                                     ((rightOperand == "false") && ((boolean) leftOperandContract.get(leftOperand) == false)))))
                                    ret = true;
                            }
                        } else if (typeOfRightOperand == compareOperandType.CONSTSTR) {          //rightOperand is CONSTANT (string)
                            if (leftOperandContract.get(leftOperand).getClass().getName().endsWith("Role")) {
                                KeyAddress ka = new KeyAddress(rightOperand);
                                ret = ka.isMatchingKeyAddress((leftOperandContract.get(leftOperand)));
                                if (indxOperator == NOT_EQUAL)
                                    ret = !ret;
                            } else if (((indxOperator == NOT_EQUAL) && (!leftOperandContract.get(leftOperand).equals(rightOperand))) ||
                                       ((indxOperator == EQUAL) && (leftOperandContract.get(leftOperand).equals(rightOperand))))
                                ret = true;
                        }

                        break;

                    case MATCHES:


                        break;

                    default:
                        throw new IllegalArgumentException("Invalid operator in condition");
                }
            }
            catch (Exception e){}
        }else{       //if rightOperand == null, then operation: defined / undefined
            if (indxOperator == DEFINED) {
                try {
                    if (leftOperandContract.get(leftOperand) != null)
                        ret = true;
                } catch (Exception e) {}
            } else if (indxOperator == UNDEFINED) {
                try {
                    ret = (leftOperandContract.get(leftOperand) == null);
                }
                catch (Exception e) {
                    ret = true;
                }
            }
            else
                throw new IllegalArgumentException("Invalid operator in condition");
        }

        return ret;
    }

    /**
     * Check condition of reference
     * @param condition condition to check for matching
     * @param ref contract to check for matching
     * @param contracts contract list to check for matching
     * @param iteration check inside references iteration number
     * @return true if match or false
     */
    public boolean checkCondition(String condition, Contract ref, Collection<Contract> contracts, int iteration) {

        for (int i = 0; i < 2; i++) {
            int operPos = condition.lastIndexOf(operators[i]);

            if ((operPos >= 0) && (condition.length() - operators[i].length() == operPos)) {
                String leftOperand = condition.substring(0, operPos).replaceAll("\\s+", "");
                return compareOperands(ref, leftOperand, null, compareOperandType.CONSTOTHER, i, contracts, iteration);
            }
        }

        for (int i = 2; i < 9; i++) {
            int operPos = condition.indexOf(operators[i]);
            int markPos = condition.indexOf("\"");

            if ((operPos < 0) || ((markPos >= 0) && (operPos > markPos)))
                continue;

            String leftOperand = condition.substring(0, operPos).replaceAll("\\s+", "");

            String subStrR = condition.substring(operPos + operators[i].length());
            if (subStrR.length() == 0)
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Missing right operand.");

            int rmarkPos1 = subStrR.indexOf("\"");
            int rmarkPos2 = subStrR.lastIndexOf("\"");

            if ((rmarkPos1 >= 0) && (rmarkPos1 == rmarkPos2))
                throw new IllegalArgumentException("Invalid format of condition: " + condition + ". Only one quote is found for string.");

            String rightOperand;
            compareOperandType typeRightOperand = compareOperandType.CONSTOTHER;

            if ((rmarkPos1 >= 0) && (rmarkPos1 != rmarkPos2)) {
                rightOperand = subStrR.substring(rmarkPos1 + 1, rmarkPos2);
                typeRightOperand = compareOperandType.CONSTSTR;
            }
            else {
                rightOperand = subStrR.replaceAll("\\s+", "");
                if (rightOperand.contains("."))
                    typeRightOperand = compareOperandType.FIELD;
            }

            return compareOperands(ref, leftOperand, rightOperand, typeRightOperand, i, contracts, iteration);
        }

        throw new IllegalArgumentException("Invalid format of condition: " + condition);
    }

    /**
     * Check conditions of reference
     * @param conditions binder with conditions to check for matching
     * @param ref contract to check for matching
     * @param contracts contract list to check for matching
     * @param iteration check inside references iteration number
     * @return true if match or false
     */
    public boolean checkConditions(Binder conditions, Contract ref, Collection<Contract> contracts, int iteration) {

        boolean result;

        if (conditions.containsKey(all_of.name()))
        {
            List<Object> condList = conditions.getList(all_of.name(), null);
            if (condList == null)
                throw new IllegalArgumentException("Expected all_of conditions");

            result = true;
            for (Object item: condList) {
                if (item.getClass().getName().endsWith("String"))
                    result = result && checkCondition((String) item, ref, contracts, iteration);
                else
                    //LinkedHashMap<String, Binder> insideHashMap = (LinkedHashMap<String, Binder>) item;
                    //Binder insideBinder = new Binder(insideHashMap);
                    result = result && checkConditions((Binder) item, ref, contracts, iteration);
            }
        }
        else if (conditions.containsKey(any_of.name()))
        {
            List<Object> condList = conditions.getList(any_of.name(), null);
            if (condList == null)
                throw new IllegalArgumentException("Expected any_of conditions");

            result = false;
            for (Object item: condList) {
                if (item.getClass().getName().endsWith("String"))
                    result = result || checkCondition((String) item, ref, contracts, iteration);
                else
                    //LinkedHashMap<String, Binder> insideHashMap = (LinkedHashMap<String, Binder>) item;
                    //Binder insideBinder = new Binder(insideHashMap);
                    result = result || checkConditions((Binder) item, ref, contracts, iteration);
            }
        }
        else {
            result = false;
//            throw new IllegalArgumentException("Expected all_of or any_of");
        }

        return result;
    }

    /**
     * Check if reference is valid i.e. have matching with criteria items
     * @return true or false
     */
    public boolean isValid() {
        if(matchingItems.size() == 0) {
            System.err.println("bad ref: " + name + " " + baseContract.getId());
        }
        return matchingItems.size() > 0;
    }

    /**
     * Check if given item matching with current reference criteria
     * @param a item to check for matching
     * @param contracts contract list to check for matching
     * @return true if match or false
     */
    public boolean isMatchingWith(Approvable a, Collection<Contract> contracts) {
        return isMatchingWith(a, contracts, 0);
    }

    /**
     * Check if given item matching with current reference criteria
     * @param a item to check for matching
     * @param contracts contract list to check for matching
     * @param iteration check inside references iteration number
     * @return true if match or false
     */
    public boolean isMatchingWith(Approvable a, Collection<Contract> contracts, int iteration) {
        //todo: add this checking for matching with given item

        if (iteration > 16)
            throw new IllegalArgumentException("Recursive checking references have more 16 iterations");

        boolean result = true;

        if(a instanceof Contract) {
            //check roles
            Contract contract = (Contract) a;
            if (result) {
                Map<String, Role> contractRoles = contract.getRoles();
                result = roles.isEmpty() || roles.stream().anyMatch(role -> contractRoles.containsKey(role));
            }

            //check origin
            if (result) {
                result = (origin == null || !(contract.getOrigin().equals(origin)));
            }


            //check fields
            if (result) {
                Binder stateData = contract.getStateData();
                result = fields.isEmpty() || fields.stream().anyMatch(field -> stateData.get(field) != null);
            }

            //check conditions
            if (result) {
                result = checkConditions(conditions, contract, contracts, iteration);
            }
        }

        return result;
    }






    public String getName() {
        return name;
    }

    public Reference setName(String name) {
        this.name = name;
        return this;
    }

    public List<String> getRoles() {
        return roles;
    }

    public Reference addRole(String role) {
        this.roles.add(role);
        return this;
    }

    public Reference setRoles(List<String> roles) {
        this.roles = roles;
        return this;
    }

    public List<String> getFields() {
        return fields;
    }

    public Reference addField(String field) {
        this.fields.add(field);
        return this;
    }

    public Reference setFields(List<String> fields) {
        this.fields = fields;
        return this;
    }

    public Binder getConditions() {
        return conditions;
    }

    public Reference setConditions(Binder conditions) {
        this.conditions = conditions;
        return this;
    }

    public Reference addMatchingItem(Approvable a) {
        this.matchingItems.add(a);
        return this;
    }

    public Contract getContract() {
        return baseContract;
    }

    public Reference setContract(Contract contract) {
        baseContract = contract;
        return this;
    }

    @Override
    public String toString() {
        String res = "{";
        res += "name:"+name;
        res += ", type:"+type;
        if (transactional_id.length() > 8)
            res += ", transactional_id:"+transactional_id.substring(0, 8)+"...";
        else
            res += ", transactional_id:"+transactional_id;
        res += ", contract_id:"+contract_id;
        res += ", required:"+required;
        res += ", origin:"+origin;
        res += ", signed_by:[";
        for (int i = 0; i < signed_by.size(); ++i) {
            if (i > 0)
                res += ", ";
            Role r = signed_by.get(i);
            res += r.getName() + ":" + Bytes.toHex(r.getKeys().iterator().next().fingerprint()).substring(0, 8) + "...";
        }
        res += "]";
        res += "}";
        return res;
    }





    static {
        DefaultBiMapper.registerClass(Reference.class);
    }
};
