package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode

internal class V31__navnendring_førstegangsbehandling_til_søknad: BehandlingshendelseJsonMigrering() {
    override fun query() = """
        select sekvensnummer, data, er_korrigert from behandlingshendelse
        where data ->> 'behandlingtype' = 'FØRSTEGANGSBEHANDLING';
        
    """

    override fun nyVersjon() = null

    override fun nyData(gammelData: ObjectNode): ObjectNode {
        gammelData.put("behandlingtype", "SØKNAD")
        return gammelData
    }
}