Uniclient Command-Line tool (beta)
==================================
User manual
-----------

### Overview

**Warning**: This document describes early-access beta version of the uniclient tool. While it is functional, its interface is a subject to change without notice. The changelog is available as the `Release_Notes.md` document.

Universa CLI tool allows to use Universa network with no line of code. It is optimized for both console use by the operator and integration with other software being able to reformat output to the structured machine-readable JSON form.

The client has many features and self documents as usual when executed as `uniclient --usage`, or without arguments.

**Important. This is a beta version, functional but under constant development.** It contains all the basic functionality but still there are many features and improvements we are working on. Sorry for inconvenience and please be patient.

### Installation

JRE 1.8+ is required to run uniclient, it coulbe download from the [Oracle downloads site](http://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html).

Download the current version of the uniclient tool from 
[our cloud storage](https://drive.google.com/file/d/1ODg3CIn93NZPVFF7HWWG3BR0XqdOKDZ4/view) and unzip it. Now you can start it with the bundled `uniclient` command on unix or `uniclient.bat` on windows. Alternatively, you can start it directly using `java -jar uniclient.jar`.

Executing `uniclient` with no arguments will show built-in help.

### Usage scenarios

#### Create a new smart contract from DSL template

The DSL templates are useful to create new contracts. There are some DSL templates in the Github to play with. The DSL template does differ from the exported contract: the latter is more machine-style than human readable.

If you have DSL template stored in the `.yaml` file, e.g. `template.yaml`, you can create new contract by typing in the command line:

    uniclient --create template.yaml

or

    uniclient -с template.yaml

Contract will be saved as `.unicon` file in the same path and will have the same name as the template.

You can specify the name of the file contract will be saved to by adding option `--name` with filename parameter:

    uniclient -c template.yaml --name MyContract

The created contract will be saved as `MyContract.unicon` file.

Of course, you can create multiple contracts via:

    uniclient -с template1.yaml template2.yaml

or comma-separated:

    uniclient -с template1.yaml,template2.yaml

or specify a name for each contract independently:

    uniclient -c template.yaml --name contract1.unicon template.yaml --name contract2.unicon

#### Import contract from a specified XML, JSON or YAML file

To automate contract processing in your system. It is advisable to export contract to XML/JSON/YAML, edit it and import it back to binary form to seal and register with the Universa network.

If you have JSON, XML or YAMLrepresentation of a contract stored in the file, e.g. `contract.json`, you can import contract from that file by typing the command:

    uniclient --import contract.json

or:

    uniclient -i contract.json

Contract will be saved as an `.unicon` file with same name as given file name of a structure.

You can specify the name of the file imported contract will be saved to by adding option `--name` with filename parameter:

    uniclient -i contract.json --name MyContract

Contract will be saved as `MyContract.unicon` file.

Of course, you can do import for multiple files via:

    uniclient -i contract1.json contract2.xml

or comma-separated:

    uniclient -i contract1.json,contract2.xml

or specify a name for each contract independently:

    uniclient -i contract1.json --name contract1.unicon contract2.xml --name contract2.unicon

**Important:** the imported contract will have no signatures unless the `-k` option was provided, see “Signing the contract” section below.

#### Export contract to XML, JSON or YAML file

This is important if you want to see or manually modify the state of the contract. To convert a binary sealed `contract.unicon` file use:

    uniclient --export contract.unicon

or

    uniclient -e contract.unicon

The contract will be saved as a `.json` file with same name as given contract file name by default.

You can specify export format by adding option `--as` and typing one of possible values: `json`, `xml` or `yaml`:

    uniclient -e contract.unicon --as <format>

where `<format>` is one of: `xml`, `json`, `yaml`.

Use `--pretty` key to export contract to JSON with pretty formatting:

    uniclient -e contract.unicon --as json --pretty

You can specify name of file contract will be export to by adding option `-o` or `--name` parameter with filename argument immediately following it:

    uniclient -e contract.unicon --as xml --o MyContract

The contract will be saved as `MyContract.xml` file. If you specify the name of contract you can omit command `--as` if the extension of target file matches the format you want to export.

Use `-o /dev/stdout` (in the Unix-compatible environments) to print the exported structure to the console:

    uniclient -e test1.unicon -o /dev/stdout --as yaml

In the Windows terminal use `-o con` instead.

Of course, you can do export for multiple files via:

    uniclient -e contract1.unicon contract2.unicon

or comma-separated:

    uniclient -e contract1.unicon,contract2.unicon

or specify a name for each contract independently:

    uniclient -e contract1.unicon --name contract1.json contract2.unicon --name contract2.xml

Type the `--as` option once to choose format for export for all contracts:

    uniclient -e contract1.unicon contract2.unicon --as yaml

or specify the format for each contract separately:

    uniclient -e contract1.unicon --as xml contract2.unicon --as yaml

#### Updating fields

During create, import or export operations you can update fields of the result contract by using pair of options `--set` and `--value`, e.g.:

    uniclient -e contract.unicon --set "definition.expires_at" --value "{\"definition.expires_at\": {\"seconds\": 6373524323,\"__type\":\"unixtime\"}}"

will export contract with the updated expiration date. Note: use as value json, xml or yaml string with root element called same as field name you want to update. Uniclient will determine the format of the value string automatically.

#### Extracting keys

If you need to get a public key from some specific role defined in the contract, use `--extract-key` option specifying the role name:

    uniclient -e contract.unicon --extract-key owner

The extracted key will be saved to `.public.unikey` file in the common Universa key file format.

When modifying the exported text files it might be useful to extract the keys in BASE64 format which could be done adding the `--base64` argument. You can still export the keys from multiple contracts as described at the “Export contract” section.

#### Check contract locally

It is advised to check the contract state locally before sending it to the network. For it, you can use the following command:

    uniclient --check contract.unicon

Add `--verbose` to see more details. Use `--json` to have the uniclient format its output in the machine-readable format for further processing. For example,

    uniclient --check test1.unicon --json

produces the following output:

~~~
{
   "messages":[
      "",
      "Checking loaded contracts",
      "Checking contract at /Users/home/uniclient-test/test1.unicon",
      "Contract is valid"
  ],
   "errors":[]
}
~~~

The successful result is confirmed by the empty `errors` array or by analysing the exit status, which is `0` for success; but it is recommended to check the errors to get more information on what is wrong.

Also you can check all contracts in the specified path:

    uniclient -ch dir_to_check

or do it recursively:

    uniclient -ch dir_to_check -r

Of course, you can check multiple files via:

    uniclient -ch contract1.unicon contract2.unicon

or comma-separated:

    uniclient -ch contract1.unicon,contract2.unicon

or passing multiple paths:

    uniclient -ch dir_to_check1 dir_to_check2

#### Search for contracts in a directory

You may find contracts at the specified path using the following command:

    uniclient --find my_path

or

    uniclient -f my_path

The found contracts will be printed to the console. As usual, `-r` will search the directory tree.

    uniclient -f my_path -r

Of course, you can looking for multiple paths via:

    uniclient -f my_path1 my_path2

or comma-separated:

    uniclient -f my_path1,my_path2

#### Generating key pairs

The command like

    uniclient -g key_name_prefix

will create a key pair files `key_name_prefix.public.unikey` and `key_name_prefix.private.unikey`. Keep them safe and never transmit your private key over the network except that inside of some very well encrypted containers (such as Universa CryptoCloud capsules).

#### Signing the contract

Universa requires each new contract or contract revision to be properly signed by the required parties. To do it, seal the contract while creating it:

    uniclient -c some_dsl_template.yaml -k your.private.unikey

Also, you should provide proper signing keys when importing any text format (see import section above), with the `-`k option. Note that the `-k` option should precede `-i`:

    uniclient -k sample.private.unikey -i test1.yml

The improperly signed contracts will not be accepted by the Universa network.

#### Registering and revoking the contract in the Universa network

When the contract is prepared and checked (using the `--check` option), it is a time to register it in the Universa network. From version 3.0.1 contract's processing should be paid bu transaction units. Point to you transaction units contract with key `--tu`. To register contract, run the command like:

    uniclient --register contract.unicon --tu tu.unicon

This command submits the contract for approval. The response will be similar to:

~~~
registering the contract SWlZ0U73oUJ3hWLeIFAJeUaU0y0CowYOxzhfAfPCQ6zouwFUyfXlJoyO1fUb1jbFoSPv/zXiAzVaEBGrdU62SA

Will try to connect to to the random node:
Node(http://node-30-com.universa.io:8080,Key(RSAPublic,None,PP7s8Mo):PP7s8Mo)

submitted with result:
ItemResult<PENDING_POSITIVE 2017-11-01T02:04:29+01:00[Europe/Warsaw] (copy)>
~~~

This means that the node 30 has initiated the voting. Notice the contract ID in the “registering the contract” line. It is a BASE64-encoded ID of the contract which will be used from now to uniquely identify it.

The status of the contract initially could be one of `PENDING` / `PENDING_NEGATIVE` / `PENDING_POSITIVE` as the starting node checks it against its copy of the shared ledger. To update the state, `--probe` it in a second or two:

    uniclient --probe SWlZ0U73oUJ3hWLeIFAJeUaU0y0CowYOxzhfAfPCQ6zouwFUyfXlJoyO1fUb1jbFoSPv/zXiAzVaEBGrdU62SA

This should be a single line. The network will answer with something like:

~~~
Will try to connect to to the random node: Node(http://node-12-com.universa.io:8080,Key(RSAPublic,None,jtxGVvs):jtxGVvs)

Universa network has reported the state:
ItemResult<APPROVED 2017-11-01T02:04:29+01:00[Europe/Warsaw] (copy)>
~~~

As stated by this listing, the system has accepted the contract the same second it was submitted.
Other possible contract states are:

* `DECLINED` – the contract can’t be accepted, for example, it has errors, wrong links, already processed.
* `REVOKED` – the contract was recently revoked. This state is not kept for long, the network has short memory of discarded items.
* `UNDEFINED` – the election failed for example due to severe network outage. You can try again soon.

Use key `--amount` to manually set the amount of transaction units will be spend for contract processing. Use approved transaction units contract with key `--tu`. After successful `--register` your file with transaction units contract will be resaved with new revision of transaction units contract and decreased amount of transaction units, so you can use it again without additionally operations.

If you registered the contract you may revoke contract via simple command (use the same private key you have used before to sign the contract):

    uniclient --revoke contract.unicon -k my_key.private.unikey

And if you check contract with `--probe` you will see status will have changed to `REVOKED`

Of course, you can register and revoke multiple contracts via:

    uniclient --register contract1.unicon contract2.unicon

or comma-separated:

    uniclient --register contract1.unicon,contract2.unicon

#### Checking the contract state

As in the example above, it is possible to ask the network about the previously submitted contract state with its ID:

    uniclient --probe SWlZ0U73oUJ3hWLeIFAJeUaU0y0CowYOxzhfAfPCQ6zouwFUyfXlJoyO1fUb1jbFoSPv/zXiAzVaEBGrdU62SA

This should be a single line. The network will answer with something like:

~~~
Will try to connect to to the random node: Node(http://node-12-com.universa.io:8080,Key(RSAPublic,None,jtxGVvs):jtxGVvs)

Universa network has reported the state:
ItemResult<APPROVED 2017-11-01T02:04:29+01:00[Europe/Warsaw] (copy)>
~~~

Of course, you can probe multiple contracts via:

    uniclient --probe SWlZ0U73oUJ3hWLeIFAJeUaU0y0CowYOxzhfAfPCQ6zouwFUyfXlJoyO1fUb1jbFoSPv/zXiAzVaEBGrdU62SA G0lCqE2TPn9wiioHDy5nllWbLkRwPA97HdnhtcCn3EDAuoDBwiZcRIGjrBftGLFWOVUY8D5yPVkEj+wqb6ytrA

or comma-separated:

    uniclient --probe SWlZ0U73oUJ3hWLeIFAJeUaU0y0CowYOxzhfAfPCQ6zouwFUyfXlJoyO1fUb1jbFoSPv/zXiAzVaEBGrdU62SA,G0lCqE2TPn9wiioHDy5nllWbLkRwPA97HdnhtcCn3EDAuoDBwiZcRIGjrBftGLFWOVUY8D5yPVkEj+wqb6ytrA

#### Packing and unpacking the contract

If you have contract stored as a packed transaction you can unpack it using:

    uniclient --unpack contract.unicon

This command extracts revoking and new items from contract and save them in the files named like `contract_new_item_1.unicon` for new items or `contract_revoke_1.unicon` for items to revoke.

And you can pack them together using:

    uniclient --pack contract.unicon --add-sibling sibling.unicon --add-revoke revoke.unicon

As a result, the `contract.unicon` file will be created from the counterpart contracts. Note: use `--name` to save the contract with the name different from the original contract.

#### Calculating cost of contract processing 

If you want to know how much cost of contract processing you registering is, add key `-cost` at the end:

    uniclient --register contract.unicon -cost

This key will calculate cost of processing and prints it to the console.


However, you can calculate cost of contract processing, but without withdraw that cost, using `--cost` as standalone:

    uniclient --cost contract.unicon

This command will calculate cost of processing and prints it to the console, but without real calls to the Universa network. 

Command works with multiple files as other commands.

#### Output control

It is possible to format all uniclient output in the JSON format for easy parsing and further processing. Use `--json` key.

#### Common use cases

**_Case_**: you want to release your own tokens. You have edited as you want dsl template (f.e. `token_dsl.yml`), keys (f.e. `my_key.private.unikey`) and bought transaction units (f.e. `tu.unicon` with `my_key.private.unikey` as owner). Do the following:

    -create token_dsl.yml -name my_token.unicon -k my_key.private.unikey    
    -register my_token.unicon -tu tuContract -k my_key.private.unikey -wait 1000
    
as result you have approved by Universa contract `my_token.unicon` as binary file. That means you released your own tokens and can do split or join operations with it.


**_Case_**: you want to release shares for your company. You have edited as you want dsl template (f.e. `shares_dsl.yml`), keys (f.e. `my_key.private.unikey`) and bought transaction units (f.e. `tu.unicon` with `my_key.private.unikey` as owner). Do the following:

    -create shares_dsl.yml -name my_company_shares.unicon -k my_key.private.unikey    
    -register my_company_shares.unicon -tu tuContract -k my_key.private.unikey -wait 1000

as result you have approved by Universa contract `my_company_shares.unicon` as binary file. That means you released shares for your company and can distribute them between your partners and so on.


**_Case_**: you have something and you want to be notarially certified as own of it. You have edited as you want dsl template (f.e. `notary_dsl.yml`), keys (f.e. `my_key.private.unikey`) and bought transaction units (f.e. `tu.unicon` with `my_key.private.unikey` as owner). Do the following:

    -create notary_dsl.yml -name notaried_stuff.unicon -k my_key.private.unikey    
    -register notaried_stuff.unicon -tu tuContract -k my_key.private.unikey -wait 1000

as result you have approved by Universa contract `notaried_stuff.unicon` as binary file. That means you certified as owner by Universa and can dispose of contract's subject.
