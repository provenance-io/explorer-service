fun Any.toMsgCreateGroup() = this.unpack(cosmos.group.v1.Tx.MsgCreateGroup::class.java)
fun Any.toMsgUpdateGroupMembers() = this.unpack(cosmos.group.v1.Tx.MsgUpdateGroupMembers::class.java)
fun Any.toMsgUpdateGroupAdmin() = this.unpack(cosmos.group.v1.Tx.MsgUpdateGroupAdmin::class.java)
fun Any.toMsgUpdateGroupMetadata() = this.unpack(cosmos.group.v1.Tx.MsgUpdateGroupMetadata::class.java)
fun Any.toMsgCreateGroupPolicy() = this.unpack(cosmos.group.v1.Tx.MsgCreateGroupPolicy::class.java)
fun Any.toMsgCreateGroupWithPolicy() = this.unpack(cosmos.group.v1.Tx.MsgCreateGroupWithPolicy::class.java)
fun Any.toMsgUpdateGroupPolicyAdmin() = this.unpack(cosmos.group.v1.Tx.MsgUpdateGroupPolicyAdmin::class.java)
fun Any.toMsgUpdateGroupPolicyDecisionPolicy() = this.unpack(cosmos.group.v1.Tx.MsgUpdateGroupPolicyDecisionPolicy::class.java)
fun Any.toMsgUpdateGroupPolicyMetadata() = this.unpack(cosmos.group.v1.Tx.MsgUpdateGroupPolicyMetadata::class.java)
fun Any.toMsgSubmitProposalGroup() = this.unpack(cosmos.group.v1.Tx.MsgSubmitProposal::class.java)
fun Any.toMsgWithdrawProposalGroup() = this.unpack(cosmos.group.v1.Tx.MsgWithdrawProposal::class.java)
fun Any.toMsgVoteGroup() = this.unpack(cosmos.group.v1.Tx.MsgVote::class.java)
fun Any.toMsgExecGroup() = this.unpack(cosmos.group.v1.Tx.MsgExec::class.java)
fun Any.toMsgLeaveGroup() = this.unpack(cosmos.group.v1.Tx.MsgLeaveGroup::class.java)





https://share.ipfs.io/#/

group
-----
id
admin
group_data
group_members
created tx info
updated tx_info

group_history
--------
id
group_id
group_data
group_members
tx_info -> the tx that caused the new state

group_policy
-------
id
group_id
policy_address
admin
policy_data
created tx info
updated tx_info

group_policy_history
-----------
id
group_id
policy_address
policy_data
tx_info -> the tx that caused the new state

group_proposal
---------
id
group_id
policy_address_id -> from group_policy table
policy_address
proposal_id
proposal_data
    data object
        proposers
        metadata
        messages
        exec type
        submit_time -> from block
        group_version
        policy_version
        nullable
            final_tally_result
            voting_period_end
proposal_node_data -> from the chain if its available
proposal_status
executor_result
tx info

group_vote
-------
id
proposal_id
address_id
address
is_validator
vote
metadata
tx info

tx_group
-----
id
block_height
tx_hash_id
tx_hash
group_id

tx_group_policy
-----
id
block_height
tx_hash_id
tx_hash
policy_address_id -> from group_policy table
policy_address

