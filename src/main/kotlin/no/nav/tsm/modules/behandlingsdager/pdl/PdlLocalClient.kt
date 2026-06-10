package no.nav.tsm.modules.behandlingsdager.pdl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.time.LocalDate
import no.nav.tsm.core.logger

class PdlLocalClient : PdlClient {
    private val logger = logger()

    override suspend fun getPerson(ident: String): Either<PdlClient.PdlErrors, PdlPerson> {
        if (ident == "does-not-exist") {
            logger.info("[PDL Mock]: Got request for ident that does not exist, returning null")
            return PdlClient.PdlErrors.NotFound.left()
        }

        logger.info("[PDL Mock]: Got request for ident $ident, returning mock person")
        return PdlPerson(
            foedselsdato = LocalDate.parse("1990-01-01"),
            identer =
                listOf(
                    PdlIdent(
                        ident = ident,
                        gruppe = PdlIdentgruppe.FOLKEREGISTERIDENT,
                        historisk = false,
                    ),
                    PdlIdent(
                        ident = "12345678912345",
                        gruppe = PdlIdentgruppe.AKTORID,
                        historisk = false,
                    ),
                ),
        )
            .right()
    }
}
