package io.provenance.explorer.service

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.entities.NameRecord
import io.provenance.explorer.domain.models.explorer.Name
import io.provenance.explorer.domain.models.explorer.NameMap
import io.provenance.explorer.domain.models.explorer.NameObj
import io.provenance.explorer.domain.models.explorer.NameTreeResponse
import io.provenance.explorer.grpc.v1.AttributeGrpcClient
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class NameService(
    private val attrClient: AttributeGrpcClient,
    private val props: ExplorerProperties
) {

    fun getNameMap() = transaction {
        val nameSet = NameRecord.getNameSet()
        val tree = mutableListOf<NameMap>()
        nameSet.forEach { recurseMainTree(it, tree, 0, nameSet) }
        val depthCount = nameSet.map { it.nameList }.maxOf { it.size }
        NameTreeResponse(tree, depthCount)
    }

    fun recurseMainTree(obj: NameObj, tree: MutableList<NameMap>, idx: Int, nameSet: List<NameObj>) {
        val segment = obj.nameList.getOrNull(idx) ?: return
        val parentObj = tree.firstOrNull { it.segmentName == segment }
        if (parentObj == null) {
            val fullName = obj.nameList.subList(0, idx + 1).reversed().joinToString(".")
            val fullObj = nameSet.firstOrNull { it.fullName == fullName } ?: updateName(fullName)
            val parent = NameMap(segment, mutableListOf(), fullName, fullObj?.owner, fullObj?.restricted ?: false)
            tree.add(parent)
        }
        val childList = tree.first { it.segmentName == segment }
        recurseMainTree(obj, childList.children, idx + 1, nameSet)
    }

    fun updateName(name: String) = runBlocking {
        attrClient.getOwnerForName(name)?.let { res ->
            val (child, parent) = name.splitChildParent()
            NameRecord.insertOrUpdate(Name(parent, child, name, res.address, true, 0))
            NameObj(name.split(".").reversed(), res.address, true, name)
        }
    }

    fun getVerifiedKycAttributes() = transaction {
        NameRecord.getNamesByOwners(props.verifiedAddresses)
            .filter { it.fullName.contains("kyc") }
            .map { it.fullName }
            .toSet()
    }
}

// Splits from `figuretest2.kyc.pb` -> Pair(`kyc.pb`, `kyc.pb`)
// Splits from `pb` -> Pair(`pb`, null)
fun String.splitChildParent() = this.split(".").let { it[0] to it.getOrNull(1) }
