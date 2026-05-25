# ADR 7: Notificatie Berichtstructuur & Templating

**Status:** Geaccepteerd

**Datum:** 21 mei 2026

**Besluitvormer:** Architectuurteam

## Context

Onze communicatiemodule moet patiënten herinneren aan hun afspraak (24u en 1u van tevoren). Om een professionele en eenduidige patiëntervaring te bieden, moeten deze berichten consistent zijn qua opmaak, maar tegelijkertijd dynamisch genoeg om specifieke afspraakdetails (tijd, locatie, instructies) te bevatten.

## Besluit

Wij hanteren een **Template-based notificatiemodel**. Het basisbericht is een vast sjabloon waarin dynamische variabelen ('placeholders') worden vervangen door de data uit ons `Appointment`-model vlak voor verzending.

## Argumentatie

### 1. Waarom Templates?
* **Consistentie:** Alle patiënten ontvangen berichten in dezelfde "tone-of-voice", wat bijdraagt aan de betrouwbaarheid van het ziekenhuis.
* **Onderhoudbaarheid:** Als de tekst van de herinnering moet worden aangepast (bijv. toevoegen van een algemene waarschuwing), hoeven we dit op één centrale plek te wijzigen in plaats van in duizenden losse berichten.
* **Privacy-by-Design:** Door het template onafhankelijk van de data te houden, voorkomen we dat ontwikkelaars per ongeluk gevoelige data hard-coden in de bericht-templates.

### 2. Dynamische Datastructuur (Placeholders)
Elk bericht wordt gegenereerd op basis van de volgende datavelden uit onze database:

| Placeholder | Bronveld (Appointment model) | Toelichting |
| :--- | :--- | :--- |
| `{scheduledTime}` | `scheduledTime` | Formatted datum en tijd (lokaal ziekenhuisformaat). |
| `{reminderType}` | Logic (24h / 1h) | Geeft de patiënt urgentie (bijv. "over 1 uur"). |
| `{location}` | `location` | De specifieke locatie/kamer. |
| `{subject}` | `subject` | Het onderwerp van de afspraak (bijv. "Controle"). |
| `{instructions}` | `instructions` | Optioneel: specifieke instructies (bijv. "Nuchter komen"). |

### 3. Implementatie in de Worker
De Worker-service fungeert als de 'generator'. Vlak voordat de Worker een bericht stuurt naar de provider (SwiftSend, etc.), ontsleutelt hij de `data_encrypted` payload, haalt de relevante velden op, en voert een 'String Replacement' uit op het template.



## Overwogen alternatieven

**Hard-coded berichten per provider** *(Rejected)*:
* **Waarom niet:** Als we voor SwiftSend, LegacyLink en AsyncFlow telkens andere berichten moeten bijhouden, wordt de code onbeheersbaar en ontstaan er fouten in de communicatie.

**Frontend-driven rendering** *(Rejected)*:
* **Waarom niet:** De UI/OpenMRS-plugin moet zo 'dom' mogelijk blijven. Het genereren van het uiteindelijke bericht is een verantwoordelijkheid van onze eigen communicatiemodule (single source of truth).

## Consequenties

* **Internationalisering (i18n):** Omdat we internationaal werken, moeten we in de toekomst rekening houden met taal-templates (bijv. Engels vs. Nederlands). De template-structuur ondersteunt dit door een `language_code` toe te voegen aan de `organizationId`.
* **Lengtebeperkingen:** Sommige providers (zoals SMS) hebben een karakterlimiet. De Worker moet een check bevatten of het ingevulde template niet te lang is voor de gekozen provider.
