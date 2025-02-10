package io.provenance.explorer.domain.models.explorer

import cosmos.base.abci.v1beta1.Abci
import tendermint.abci.Types

data class ProvenanceEvent(
    val type: String,
    val attributes: Map<String, String>
)

fun Abci.TxResponse.getProvenanceEvents(index: Int): List<ProvenanceEvent> {
    val events = mutableListOf<ProvenanceEvent>()

    this.logsList.toProvenanceEvents(index).also {
        events.addAll(it)
    }

    this.eventsList.filter {
        // Find the events that have a msg_index attribute
        // This assumes that the value across multiple events for a given message will have the same index...
        val e = it.attributesList.filter {
            it.key == "msg_index"
        }.firstOrNull()

        e != null && e.value == index.toString()
    }.forEach {
        // Convert the attributes to a list of pairs
        val attrsList = it.attributesList.map {
            Pair(it.key, it.value)
        }

        events.add(ProvenanceEvent(it.type, attrsList.toMap()))
    }

    return events
}

fun Abci.TxResponse.getProvenanceEventsAll(): List<ProvenanceEvent> = this.let {
    it.logsList.toProvenanceEvents() + it.eventsList.eventToProvenanceEvents()
}

private fun List<Abci.ABCIMessageLog>.toProvenanceEvents() = this.toProvenanceEvents(-1)
private fun List<Abci.ABCIMessageLog>.toProvenanceEvents(index: Int = -1): List<ProvenanceEvent> {
    val events = mutableListOf<ProvenanceEvent>()

    // Convert all logs to events if the index wasn't specified
    if (index == -1) {
        this.forEach {
            it.eventsList.stringEventToProvenanceEvents().also {
                events.addAll(it)
            }
        }
    } else {
        this[index].eventsList.stringEventToProvenanceEvents().also {
            events.addAll(it)
        }
    }

    return events
}

private fun List<Abci.StringEvent>.stringEventToProvenanceEvents() = this.map {
    // Convert the attributes to a list of pairs
    val attrs = it.attributesList.map {
        Pair(it.key, it.value)
    }.toMap()

    ProvenanceEvent(it.type, attrs)
}

private fun List<Types.Event>.eventToProvenanceEvents() = this.map {
    // Convert the attributes to a list of pairs
    val attrs = it.attributesList.map {
        Pair(it.key, it.value)
    }

    ProvenanceEvent(it.type, attrs.toMap())
}
