package no.nav.helse.spre.gosys.io

import org.intellij.lang.annotations.Language

@Language("json")
val mockUtbetalinger = listOf(
    """{
    "aktørId": "1265864250553",
    "fødselsnummer": "29076623080",
    "organisasjonsnummer": "974600951",
    "hendelser": [
        "94f70e8c-3e08-44fb-a802-b6f09b341c4f",
        "7f5cf9e8-6b64-470c-92c9-047df844582d",
        "f914ef0d-e97c-486a-a326-b9a608ad0f3a"
    ],
    "utbetalt": [
        {
            "mottaker": "974600951",
            "fagområde": "SPREF",
            "fagsystemId": "2CXFMKIMZBEN5AC6JBWBBXSWHI",
            "totalbeløp": 16150,
            "utbetalingslinjer": [
                {
                    "fom": "2020-06-17",
                    "tom": "2020-06-30",
                    "dagsats": 1615,
                    "beløp": 1615,
                    "grad": 100.0,
                    "sykedager": 10
                }
            ]
        },
        {
            "mottaker": "29076623080",
            "fagområde": "SP",
            "fagsystemId": "IMPMF7K4IJCI5DCQNHBDYUSTHA",
            "totalbeløp": 0,
            "utbetalingslinjer": []
        }
    ],
    "ikkeUtbetalteDager": [],
    "fom": "2020-06-01",
    "tom": "2020-06-30",
    "forbrukteSykedager": 10,
    "gjenståendeSykedager": 238,
    "godkjentAv": "P141762",
    "automatiskBehandling": false,
    "opprettet": "2020-07-06T13:21:02.769962",
    "sykepengegrunnlag": 420000.0,
    "grunnlagForSykepengegrunnlag": {
      "123456789": 1234.56,
      "987654321": 6543.21
    },
    "månedsinntekt": 35000.0,
    "maksdato": "2021-05-28",
    "system_read_count": 0,
    "system_participating_services": [
        {
            "service": "spleis",
            "instance": "spleis-794f44bf-gwf9w",
            "time": "2020-11-10T15:04:39.430608"
        }
    ],
    "@event_name": "utbetalt",
    "@id": "05f97d96-c428-49ce-a855-8acf5ce785d0",
    "@opprettet": "2020-11-10T15:04:39.430646",
    "@forårsaket_av": {
        "event_name": "behov",
        "id": "85f602b9-373a-4348-b661-844b6743cf17",
        "opprettet": "2020-11-10T15:04:31.592871"
    }
}"""
)
